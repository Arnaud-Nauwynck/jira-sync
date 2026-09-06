package fr.an.jira.repository;

import fr.an.jira.client.dtos.JiraIssueDTO;
import fr.an.jira.configuration.JiraSyncProperties;
import fr.an.jira.mapper.JiraToAnnotatedIssueMapper;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.JiraAnnotatedDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
import java.util.function.Consumer;
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
    private final Map<Integer, Map<String, JsonNode>> partitionCache = new ConcurrentHashMap<>();

    @Autowired
    public JiraIssueRepository(JiraSyncProperties props, ObjectMapper mapper) throws IOException {
        this(Path.of(props.getJiraSyncLocalDir()).resolve("issues"), mapper);
    }

    public JiraIssueRepository(Path baseDir, ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        this.baseDir = baseDir;
        if (! Files.exists(baseDir)) {
            log.warn("baseDir {} does not exist for issues, creating", baseDir);
            Files.createDirectories(baseDir);
        }
    }

    /**
     * Maps the issue to its {@link AnnotatedJiraIssueDTO} form and creates or updates it, recording
     * an "insert" or "update" change in its partition. On update, the previously persisted
     * {@code annotated} data (not coming from the source Jira server) is carried over onto the
     * newly mapped issue; on insert, {@code annotated} is left null.
     */
    public void save(JsonNode issue) {
        String key = requireKey(issue);
        int year = partitionYearOf(issue);
        Map<String, JsonNode> current = loadPartition(year);

        AnnotatedJiraIssueDTO annotatedIssue = JiraToAnnotatedIssueMapper.from(mapper.treeToValue(issue, JiraIssueDTO.class));
        JsonNode previous = current.get(key);
        String change;
        if (previous == null) {
            change = "insert";
        } else {
            change = "update";
            annotatedIssue.annotated = previousAnnotated(previous);
        }
        JsonNode annotatedNode = mapper.valueToTree(annotatedIssue);
        appendChange(year, change, key, annotatedNode);
        current.put(key, annotatedNode);
    }

    /** Extracts the "annotated" section of a previously persisted {@link AnnotatedJiraIssueDTO}, or null. */
    private JiraAnnotatedDTO previousAnnotated(JsonNode previousIssue) {
        JsonNode annotatedNode = previousIssue.path("annotated");
        return (annotatedNode.isMissingNode() || annotatedNode.isNull()) ? null
                : mapper.treeToValue(annotatedNode, JiraAnnotatedDTO.class);
    }

    /** Deletes the issue by key, recording a "delete" change, if it is currently known. */
    public boolean deleteByKey(String key) {
        for (int year : findAllPartitionYears()) {
            Map<String, JsonNode> current = loadPartition(year);
            if (current.remove(key) != null) {
                appendChange(year, "delete", key, null);
                return true;
            }
        }
        return false;
    }

    public boolean exists(String key) {
        return findByKey(key) != null;
    }

    /** Reads a single issue by key across all partitions, or null if not found. */
    public JsonNode findByKey(String key) {
        for (int year : findAllPartitionYears()) {
            JsonNode found = loadPartition(year).get(key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Lists the keys of all issues currently stored on disk. */
    public List<String> findAllKeys() {
        List<String> keys = new ArrayList<>();
        for (int year : findAllPartitionYears()) {
            keys.addAll(loadPartition(year).keySet());
        }
        return keys;
    }

    /** Reads all issues currently stored on disk, across all partitions. */
    public List<JsonNode> findAll() {
        List<JsonNode> all = new ArrayList<>();
        for (int year : findAllPartitionYears()) {
            all.addAll(loadPartition(year).values());
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
        writeSnapshot(year, loadPartition(year).values());
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
        Map<String, JsonNode> reloaded = readPartitionFromDisk(year);
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
    void importSnapshot(int year, Collection<JsonNode> issues) {
        writeSnapshot(year, issues);
        partitionCache.remove(year);
    }

    static int partitionYearOf(JsonNode issue) {
        String created = issue.path("fields").path("created").asText(null);
        if (created != null && created.length() >= 4) {
            try {
                return Integer.parseInt(created.substring(0, 4));
            } catch (NumberFormatException ignored) {
                // fall through to UNKNOWN_YEAR
            }
        }
        return UNKNOWN_YEAR;
    }

    private static String requireKey(JsonNode issue) {
        String key = issue.path("key").asText(null);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("issue is missing its 'key' field");
        }
        return key;
    }

    private Map<String, JsonNode> loadPartition(int year) {
        return partitionCache.computeIfAbsent(year, this::readPartitionFromDisk);
    }

    private Map<String, JsonNode> readPartitionFromDisk(int year) {
        long startMillis = System.currentTimeMillis();
        Map<String, JsonNode> result = new LinkedHashMap<>();
        readSnapshot(year, result);
        replayChanges(year, result);
        int millis = (int) (System.currentTimeMillis() - startMillis);
        if (millis >= logReloadPartitionThresholdMillis) {
            log.info("readPartitionFromDisk year={}, got {} issues took {} ms", year, result.size(), millis);
        }
        return result;
    }

    private void readSnapshot(int year, Map<String, JsonNode> target) {
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
                JsonNode issue = mapper.readTree(line);
                target.put(issue.path("key").asText(), issue);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read snapshot " + zipFile, e);
        }
    }

    private void replayChanges(int year, Map<String, JsonNode> target) {
        Path file = changesFile(year);
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                JsonNode rec = mapper.readTree(line);
                String change = rec.path("change").asText();
                switch (change) {
                    case "insert", "update" -> {
                        JsonNode data = rec.path("data");
                        target.put(data.path("key").asText(), data);
                    }
                    case "delete" -> target.remove(rec.path("key").asText());
                    default -> throw new IllegalStateException("unknown change type '" + change + "' in " + file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read changes log " + file, e);
        }
    }

    private void appendChange(int year, String change, String key, JsonNode issue) {
        ObjectNode rec = mapper.createObjectNode();
        rec.put("change", change);
        if (issue != null) {
            rec.set("data", issue);
        } else {
            rec.put("key", key);
        }
        String line = mapper.writeValueAsString(rec);
        try {
            Files.createDirectories(partitionDir(year));
            Files.writeString(changesFile(year), line + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append change for " + key + " in " + partitionDirName(year), e);
        }
    }

    private void writeSnapshot(int year, Collection<JsonNode> issues) {
        Path dir = partitionDir(year);
        Path zipFile = snapshotFile(year);
        Path tmpFile = dir.resolve(SNAPSHOT_FILE + ".tmp");
        try {
            Files.createDirectories(dir);
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpFile), StandardCharsets.UTF_8)) {
                zos.putNextEntry(new ZipEntry(SNAPSHOT_ENTRY));
                Writer writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);
                for (JsonNode issue : issues) {
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
    public void scanIssues(int fromYear, int toYear, BiConsumer<Integer,JsonNode> callback) {
        List<Integer> partitions = findPartitionYearBetween(fromYear, toYear);
        for (int year : partitions) {
            for (JsonNode issue : loadPartition(year).values()) {
                callback.accept(year, issue);
            }
        }
    }
}
