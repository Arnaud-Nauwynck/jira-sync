package fr.an.jira.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.an.jira.client.dtos.SourceJiraIssueDTO;
import fr.an.jira.configuration.JiraSyncProperties;
import fr.an.jira.mapper.SourceJiraToAnnotatedIssueMapper;
import fr.an.jira.rest.dtos.IssueExtraFieldsDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO;
import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Reads, writes and queries the Jira issues persisted to disk, partitioned by issue creation year
 * ("created_year={yyyy}" sub-directories), each holding:
 * - "data.ndjson.zip": a single-entry zip of the compacted snapshot of all issues in the partition
 *   (one JSON object per line).
 * - "changes.ndjson": an append-only log of inserts/updates/deletes recorded since the last
 *   {@link #compact}, one JSON object per line, shaped as
 *   {@code {"change":"insert"|"update","data":<issue>}} or {@code {"change":"delete","key":<key>}}.
 * The effective state of a partition is the snapshot with the changes log replayed on top.
 * Partitions are identified by their year as an {@code int}; issues with a missing/unparsable
 * "created" date fall into the {@link #UNKNOWN_YEAR} partition ("created_year=unknown").
 * <p>
 * Issues are held and persisted in their type-safe {@link JiraIssueDTO} (flattened/annotated)
 * shape; {@link #save} is the only place a raw source {@link JsonNode} (mirroring the Jira REST
 * API response) still comes in, immediately converted via {@link SourceJiraToAnnotatedIssueMapper}.
 */
@Component
@Slf4j
public class JiraIssueRepository {

    static final String PARTITION_PREFIX = "created_year=";
    static final int UNKNOWN_YEAR = -1;
    private static final String SNAPSHOT_FILE = "data.ndjson.zip";
    private static final String SNAPSHOT_ENTRY = "data.ndjson";
    private static final String CHANGES_FILE = "changes.ndjson";

    private final ObjectMapper mapper;

    private final Path baseDir;

    private int logReloadPartitionThresholdMillis = 0;

    /** partition year -> (issue key -> current issue), lazily loaded from disk and kept up to date. */
    private final Map<Integer, Map<String, JiraIssueDTO>> partitionCache = new ConcurrentHashMap<>();

    @Autowired
    public JiraIssueRepository(JiraSyncProperties props, ObjectMapper mapper) throws IOException {
        this(Path.of(props.getJiraSyncLocalDir()).resolve("issues"), mapper);
    }

    public JiraIssueRepository(Path baseDir, ObjectMapper mapper) throws IOException {
        // dropping null-valued fields on write, e.g. {"a":null} is persisted as {}, keeps the
        // persisted ndjson files compact; the injected mapper (used elsewhere, e.g. REST responses)
        // is left untouched.
        this.mapper = mapper.rebuild()
                .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.ALL_NON_NULL)
                .build();
        this.baseDir = baseDir;
        if (! Files.exists(baseDir)) {
            log.warn("baseDir {} does not exist for issues, creating", baseDir);
            Files.createDirectories(baseDir);
        }
    }

    /**
     * Maps the issue to its {@link JiraIssueDTO} form and creates or updates it, recording
     * an "insert" or "update" change in its partition. On update, the previously persisted
     * {@code annotated} data (not coming from the source Jira server) is carried over onto the
     * newly mapped issue; on insert, {@code annotated} is left null.
     */
    public void save(SourceJiraIssueDTO jiraSourceIssue) {
        JiraIssueDTO issue = SourceJiraToAnnotatedIssueMapper.from(jiraSourceIssue);
        String key = requireKey(issue);
        int year = partitionYearOf(issue);
        Map<String, JiraIssueDTO> cachedPartition = cachedPartitionData(year);

        JiraIssueDTO previous = cachedPartition.get(key);
        IssueChangeRecord chgRecord;
        if (previous == null) {
            chgRecord = new CreateIssueChangeRecord(issue); // may use jiraSourceIssue
        } else {
            chgRecord = new UpdateSyncIssueChangeRecord(issue); // may use jiraSourceIssue
            issue.annotated = previous.annotated;
        }
        appendChange(year, chgRecord);
        cachedPartition.put(key, issue);
    }

    public boolean exists(String key) {
        return findByKey(key) != null;
    }

    /**
     * The null-stripping mapper this repository persists with. Callers that build a
     * {@link JsonNode} snapshot outside this class (e.g. one-off migrations, via
     * {@code ObjectMapper#valueToTree}) must serialize through this mapper rather than a plain
     * one: null-value exclusion is applied when a POJO is turned into a tree, not when an
     * already-built tree is later written out, so a tree built with a different mapper keeps its
     * null fields regardless of what {@link #importSnapshot}/{@link #save} write with.
     */
    public ObjectMapper getMapper() {
        return mapper;
    }

    /** Reads a single issue by key across all partitions, or null if not found. */
    public JiraIssueDTO findByKey(String key) {
        for (int year : findAllPartitionYears()) {
            JiraIssueDTO found = cachedPartitionData(year).get(key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** get by key, throws exception if not found. */
    @Nonnull
    public JiraIssueDTO getByKey(String key) {
        JiraIssueDTO found = findByKey(key);
        if (found == null) {
            throw new IllegalArgumentException("Issue " + key + "not found");
        }
        return found;
    }

    /** Lists the keys of all issues currently stored on disk. */
    public List<String> findAllKeys() {
        List<String> keys = new ArrayList<>();
        for (int year : findAllPartitionYears()) {
            keys.addAll(cachedPartitionData(year).keySet());
        }
        return keys;
    }

    /** Reads all issues currently stored on disk, across all partitions. */
    public List<JiraIssueDTO> findAll() {
        List<JiraIssueDTO> all = new ArrayList<>();
        for (int year : findAllPartitionYears()) {
            all.addAll(cachedPartitionData(year).values());
        }
        return all;
    }

    /** Lists the years of the "created_year=yyyy" partitions currently present on disk. */
    public List<Integer> findAllPartitionYears() {
        if (!Files.exists(baseDir)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(baseDir)) {
            return dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(PARTITION_PREFIX))
                    .map(JiraIssueRepository::parseYear)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list " + baseDir, e);
        }
    }

    /** Lists the partition years within [fromYear, toYear] that currently exist on disk. */
    public List<Integer> findPartitionYearBetween(int fromYear, int toYear) {
        List<Integer> res = new ArrayList<>();
        List<Integer> allPartitions = findAllPartitionYears();
        for (int year : allPartitions) {
            if (year >= fromYear && year <= toYear) {
                res.add(year);
            }
        }
        return res;
    }

    /** Folds the pending changes log into a fresh compacted snapshot, then clears the changes log. */
    public void compact(int year) {
        writeSnapshot(year, cachedPartitionData(year).values());
        clearChangesFile(year);
    }

    public void compactAll() {
        for (int year : findAllPartitionYears()) {
            compact(year);
        }
    }

    /**
     * Discards any cached in-memory state for the partition, reloads it from disk (snapshot +
     * replayed changes), then rewrites the compacted snapshot and clears the changes log.
     * Unlike {@link #compact}, this ignores whatever is currently cached in memory.
     */
    public void recompact(int year) {
        recompactPartition(year);
    }

    /** Applies {@link #recompact} to every partition currently present on disk. */
    public void recompactAll() {
        for (int year : findAllPartitionYears()) {
            recompactPartition(year);
        }
    }

    private void recompactPartition(int year) {
        Map<String, JiraIssueDTO> reloaded = readPartitionFromDisk(year);
        partitionCache.put(year, reloaded);
        writeSnapshot(year, reloaded.values());
        clearChangesFile(year);
    }

    private void clearChangesFile(int year) {
        try {
            Files.deleteIfExists(changesFile(year));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to clear changes log for " + partitionDirName(year), e);
        }
    }

    /** Used by the legacy flat-file migration to seed a partition's compacted snapshot directly. */
    void importSnapshot(int year, Collection<JiraIssueDTO> issues) {
        writeSnapshot(year, issues);
        partitionCache.remove(year);
    }

    static int partitionYearOf(JiraIssueDTO issue) {
        String created = issue.fields != null ? issue.fields.created : null;
        if (created != null && created.length() >= 4) {
            try {
                return Integer.parseInt(created.substring(0, 4));
            } catch (NumberFormatException ignored) {
                // fall through to UNKNOWN_YEAR
            }
        }
        return UNKNOWN_YEAR;
    }

    private static String requireKey(JiraIssueDTO issue) {
        String key = issue.key;
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("issue is missing its 'key' field");
        }
        return key;
    }

    private Map<String, JiraIssueDTO> cachedPartitionData(int year) {
        return partitionCache.computeIfAbsent(year, this::readPartitionFromDisk);
    }

    private Map<String, JiraIssueDTO> readPartitionFromDisk(int year) {
        long startMillis = System.currentTimeMillis();
        Map<String, JiraIssueDTO> result = new LinkedHashMap<>();
        try {
            readSnapshot(year, result);
        } catch(Exception ex) {
            log.error("Failed to read issue snapshot for partition year=" + year, ex);
            // throw new RuntimeException("Failed to read issue snapshot for partition year=" + year, ex);
        }
        replayChanges(year, result);
        int millis = (int) (System.currentTimeMillis() - startMillis);
        if (millis >= logReloadPartitionThresholdMillis) {
            log.info("readPartitionFromDisk year={}, got {} issues took {} ms", year, result.size(), millis);
        }
        return result;
    }

    private void readSnapshot(int year, Map<String, JiraIssueDTO> target) {
        Path zipFile = snapshotFile(year);
        if (!Files.exists(zipFile)) {
            return;
        }
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JiraIssueDTO issue = mapper.readValue(line, JiraIssueDTO.class);
                target.put(issue.key, issue);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read snapshot " + zipFile, e);
        }
    }

    public enum IssueChangeType {
        create, // importSync
        update, // updateSync
        updateAnnotation,
        removeAnnotation
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.PROPERTY, property="change")
    @JsonSubTypes({ //
            @JsonSubTypes.Type(value = CreateIssueChangeRecord.class, name = "create"), //
            @JsonSubTypes.Type(value = UpdateSyncIssueChangeRecord.class, name = "update"), //
            @JsonSubTypes.Type(value = UpdateAnnotationSyncIssueChangeRecord.class, name = "updateAnnotation"), //
            @JsonSubTypes.Type(value = RemoveAnnotationSyncIssueChangeRecord.class, name = "removeAnnotation") //
    })
    public static abstract class IssueChangeRecord {
        // LocalDateTime timestamp;
        public abstract IssueChangeType getChange();
        public abstract String key();
    }

    @AllArgsConstructor
    public static class CreateIssueChangeRecord extends IssueChangeRecord {
        public JiraIssueDTO data;

        @Override
        public IssueChangeType getChange() { return IssueChangeType.create; }

        @Override
        public String key() {
            return data.key;
        }
    }

    @AllArgsConstructor
    public static class UpdateSyncIssueChangeRecord extends IssueChangeRecord {
        public JiraIssueDTO data;
        // public String key;
        // public IssueFieldsDTO fields;
        // public List<IssueHistoryDTO> histories;

        @Override
        public IssueChangeType getChange() { return IssueChangeType.update; }

        @Override
        public String key() {
            return data.key;
        }
    }

    @AllArgsConstructor
    public static class UpdateAnnotationSyncIssueChangeRecord extends IssueChangeRecord {
        public String key;
        public IssueExtraFieldsDTO annotated;

        @Override
        public IssueChangeType getChange() { return IssueChangeType.updateAnnotation; }

        @Override
        public String key() {
            return key;
        }
    }

    @AllArgsConstructor
    public static class RemoveAnnotationSyncIssueChangeRecord extends IssueChangeRecord {
        public String key;

        @Override
        public IssueChangeType getChange() { return IssueChangeType.removeAnnotation; }

        @Override
        public String key() {
            return key;
        }

    }



    private void replayChanges(int year, Map<String, JiraIssueDTO> issueByKey) {
        Path file = changesFile(year);
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                IssueChangeRecord rec = mapper.readValue(line, IssueChangeRecord.class);
                if (rec instanceof CreateIssueChangeRecord chg) {
                    JiraIssueDTO newData = chg.data;
                    JiraIssueDTO prev = issueByKey.put(newData.key, newData);
                    // assert prev == null;
                } else if (rec instanceof UpdateSyncIssueChangeRecord chg) {
                    JiraIssueDTO syncData = chg.data; // may be limited to source jira data (not annotation)
                    val key = syncData.key;
                    JiraIssueDTO prev = issueByKey.get(key);
                    if (prev != null) {
                        // preserve annotation if any
                        // val prevAnnotation = prev.getAnnotated();
                        prev.setFields(syncData.fields);
                        prev.setHistories(syncData.histories);
                    } else {
                        // should not occur
                        issueByKey.put(key, syncData);
                    }
                } else if (rec instanceof UpdateAnnotationSyncIssueChangeRecord chg) {
                    val key = chg.key;
                    val newAnnotation = chg.annotated;
                    JiraIssueDTO prev = issueByKey.get(key);
                    if (prev != null) {
                        prev.setAnnotated(newAnnotation);
                    } // else should not occur, ignore anyway
                } else if (rec instanceof RemoveAnnotationSyncIssueChangeRecord chg) {
                    val key = chg.key;
                    JiraIssueDTO prev = issueByKey.get(key);
                    if (prev != null) {
                        prev.setAnnotated(null);
                    } // else should not occur, ignore anyway
                } else {
                    log.warn("unexpected change type " + rec.getChange() + " in file '" + file + "' ... ignore");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read changes log " + file, e);
        }
    }

    private void appendChange(int year, IssueChangeRecord chgRecord) {
        String line = mapper.writeValueAsString(chgRecord);
        try {
            Files.createDirectories(partitionDir(year));
            Files.writeString(changesFile(year), line + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append change for " + chgRecord.key() + " in " + partitionDirName(year), e);
        }
    }

    private void writeSnapshot(int year, Collection<JiraIssueDTO> issues) {
        Path dir = partitionDir(year);
        Path zipFile = snapshotFile(year);
        Path tmpFile = dir.resolve(SNAPSHOT_FILE + ".tmp");
        try {
            Files.createDirectories(dir);
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpFile), StandardCharsets.UTF_8)) {
                zos.putNextEntry(new ZipEntry(SNAPSHOT_ENTRY));
                Writer writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);
                for (JiraIssueDTO issue : issues) {
                    writer.write(mapper.writeValueAsString(issue));
                    writer.write("\n");
                }
                writer.flush();
                zos.closeEntry();
            }
            Files.move(tmpFile, zipFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write snapshot for " + partitionDirName(year), e);
        }
    }

    private Path partitionDir(int year) {
        return baseDir.resolve(partitionDirName(year));
    }

    private Path snapshotFile(int year) {
        return partitionDir(year).resolve(SNAPSHOT_FILE);
    }

    private Path changesFile(int year) {
        return partitionDir(year).resolve(CHANGES_FILE);
    }

    static String partitionDirName(int year) {
        return year == UNKNOWN_YEAR ? PARTITION_PREFIX + "unknown" : PARTITION_PREFIX + year;
    }

    private static int parseYear(String partitionDirName) {
        try {
            return Integer.parseInt(partitionDirName.substring(PARTITION_PREFIX.length()));
        } catch (NumberFormatException e) {
            return UNKNOWN_YEAR;
        }
    }

    /** Streams every issue whose "created_year" partition falls within [fromYear, toYear] to the callback. */
    public void scanIssues(int fromYear, int toYear, BiConsumer<Integer,JiraIssueDTO> callback) {
        List<Integer> partitions = findPartitionYearBetween(fromYear, toYear);
        for (int year : partitions) {
            Map<String, JiraIssueDTO> issueByKey = cachedPartitionData(year);
            for (JiraIssueDTO issue : issueByKey.values()) {
                callback.accept(year, issue);
            }
        }
    }


    public void putAnnotation(String key, IssueExtraFieldsDTO annotated) {
        JiraIssueDTO issue = getByKey(key); // point to cached partition data... updating => update cache!
        issue.setAnnotated(annotated);
        int year = partitionYearOf(issue);
        appendChange(year, new UpdateAnnotationSyncIssueChangeRecord(key, annotated));
    }

    public void removeAnnotation(String key) {
        JiraIssueDTO issue = getByKey(key); // point to cached partition data... updating => update cache!
        issue.setAnnotated(null);
        int year = partitionYearOf(issue);
        appendChange(year, new RemoveAnnotationSyncIssueChangeRecord(key));
    }

}
