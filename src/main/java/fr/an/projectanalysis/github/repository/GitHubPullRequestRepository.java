package fr.an.projectanalysis.github.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubPullRequestDTO;
import fr.an.projectanalysis.github.configuration.GitHubSyncProperties;
import fr.an.projectanalysis.github.mapper.SourceGitHubToAnnotatedPullRequestMapper;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestExtraFieldsDTO;
import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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
import java.time.Instant;
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
 * Reads, writes and queries the GitHub pull requests persisted to disk, partitioned by PR
 * creation year ("created_year={yyyy}" sub-directories), each holding:
 * - "data.ndjson.zip": a single-entry zip of the compacted snapshot of all PRs in the partition
 *   (one JSON object per line).
 * - "changes.ndjson": an append-only log of inserts/updates recorded since the last
 *   {@link #compact}, one JSON object per line, shaped as {@code {"change":"create"|"update","data":<pr>}}.
 * The effective state of a partition is the snapshot with the changes log replayed on top.
 * Partitions are identified by their year as an {@code int}; PRs with a missing/unparsable
 * "created" date fall into the {@link #UNKNOWN_YEAR} partition ("created_year=unknown").
 * <p>
 * Mirrors {@code fr.an.jira.repository.JiraIssueRepository}'s on-disk format, keyed by PR number
 * instead of issue key; on every "create"/"update" change, the previously persisted
 * {@code annotated} data (see {@link GitHubPullRequestDTO#annotated}) is carried over onto the
 * newly mapped PR, so a local annotation survives a re-sync. {@link #putAnnotation} and
 * {@link #removeAnnotation} record dedicated "updateAnnotation"/"removeAnnotation" changes.
 */
@Component
@Slf4j
public class GitHubPullRequestRepository {

    static final String PARTITION_PREFIX = "created_year=";
    static final int UNKNOWN_YEAR = -1;
    private static final String SNAPSHOT_FILE = "data.ndjson.zip";
    private static final String SNAPSHOT_ENTRY = "data.ndjson";
    private static final String CHANGES_FILE = "changes.ndjson";

    private final ObjectMapper mapper;

    private final Path baseDir;

    /** partition year -> (PR number -> current PR), lazily loaded from disk and kept up to date. */
    private final Map<Integer, Map<Integer, GitHubPullRequestDTO>> partitionCache = new ConcurrentHashMap<>();

    @Autowired
    public GitHubPullRequestRepository(GitHubSyncProperties props, ObjectMapper mapper) throws IOException {
        this(Path.of(props.getGithubSyncLocalDir()).resolve("pulls"), mapper);
    }

    public GitHubPullRequestRepository(Path baseDir, ObjectMapper mapper) throws IOException {
        // dropping null-valued fields on write keeps the persisted ndjson files compact; the
        // injected mapper (used elsewhere, e.g. REST responses) is left untouched.
        this.mapper = mapper.rebuild()
                .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.ALL_NON_NULL)
                .build();
        this.baseDir = baseDir;
        if (!Files.exists(baseDir)) {
            log.warn("baseDir {} does not exist for github pulls, creating", baseDir);
            Files.createDirectories(baseDir);
        }
    }

    /**
     * Maps the pull request to its {@link GitHubPullRequestDTO} form and creates or updates it,
     * recording a "create" or "update" change in its partition. On update, the previously
     * persisted {@code annotated} data (not coming from the source GitHub server) is carried over
     * onto the newly mapped PR; on insert, {@code annotated} is left null.
     */
    public void save(SourceGitHubPullRequestDTO sourcePr) {
        GitHubPullRequestDTO pr = SourceGitHubToAnnotatedPullRequestMapper.from(sourcePr);
        int number = requireNumber(pr);
        int year = partitionYearOf(pr);
        Map<Integer, GitHubPullRequestDTO> cachedPartition = cachedPartitionData(year);

        GitHubPullRequestDTO previous = cachedPartition.get(number);
        PullRequestChangeRecord chgRecord;
        if (previous == null) {
            chgRecord = new CreatePullRequestChangeRecord(pr);
        } else {
            pr.annotated = previous.annotated;
            chgRecord = new UpdatePullRequestChangeRecord(pr);
        }
        appendChange(year, chgRecord);
        cachedPartition.put(number, pr);
    }

    public boolean exists(int number) {
        return findByNumber(number) != null;
    }

    /** Reads a single PR by number across all partitions, or null if not found. */
    public GitHubPullRequestDTO findByNumber(int number) {
        for (int year : findAllPartitionYears()) {
            GitHubPullRequestDTO found = cachedPartitionData(year).get(number);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** get by number, throws exception if not found. */
    @Nonnull
    public GitHubPullRequestDTO getByNumber(int number) {
        GitHubPullRequestDTO found = findByNumber(number);
        if (found == null) {
            throw new IllegalArgumentException("Pull request #" + number + " not found");
        }
        return found;
    }

    /** Lists the numbers of all PRs currently stored on disk. */
    public List<Integer> findAllNumbers() {
        List<Integer> numbers = new ArrayList<>();
        for (int year : findAllPartitionYears()) {
            numbers.addAll(cachedPartitionData(year).keySet());
        }
        return numbers;
    }

    /** Reads all PRs currently stored on disk, across all partitions. */
    public List<GitHubPullRequestDTO> findAll() {
        List<GitHubPullRequestDTO> all = new ArrayList<>();
        for (int year : findAllPartitionYears()) {
            all.addAll(cachedPartitionData(year).values());
        }
        return all;
    }

    /**
     * Dumps the number and last-known {@code updatedAt} of every PR currently stored locally,
     * across all partitions. Used by the initial-load sync to tell, for each PR number in the
     * repo's number range, whether it still needs to be fetched (i.e. is absent from this map) —
     * see {@code GitHubPrSyncRunner#initialLoad}.
     */
    public Map<Integer, Instant> listAllPRNumberWithUpdateTime() {
        Map<Integer, Instant> res = new LinkedHashMap<>();
        for (int year : findAllPartitionYears()) {
            for (GitHubPullRequestDTO pr : cachedPartitionData(year).values()) {
                res.put(pr.number, parseInstantOrNull(pr.updatedAt));
            }
        }
        return res;
    }

    private static Instant parseInstantOrNull(String isoInstant) {
        if (isoInstant == null) {
            return null;
        }
        try {
            return Instant.parse(isoInstant);
        } catch (Exception e) {
            return null;
        }
    }

    /** Streams every PR whose "created_year" partition falls within [fromYear, toYear] to the callback. */
    public void scanPullRequests(int fromYear, int toYear, BiConsumer<Integer, GitHubPullRequestDTO> callback) {
        for (int year : findPartitionYearBetween(fromYear, toYear)) {
            for (GitHubPullRequestDTO pr : cachedPartitionData(year).values()) {
                callback.accept(year, pr);
            }
        }
    }

    /** Lists the partition years within [fromYear, toYear] that currently exist on disk. */
    public List<Integer> findPartitionYearBetween(int fromYear, int toYear) {
        List<Integer> res = new ArrayList<>();
        for (int year : findAllPartitionYears()) {
            if (year >= fromYear && year <= toYear) {
                res.add(year);
            }
        }
        return res;
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
                    .map(GitHubPullRequestRepository::parseYear)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list " + baseDir, e);
        }
    }

    public void putAnnotation(int number, GitHubPullRequestExtraFieldsDTO annotated) {
        GitHubPullRequestDTO pr = getByNumber(number); // points to cached partition data... updating => update cache!
        pr.annotated = annotated;
        int year = partitionYearOf(pr);
        appendChange(year, new UpdateAnnotationPullRequestChangeRecord(number, annotated));
    }

    public void removeAnnotation(int number) {
        GitHubPullRequestDTO pr = getByNumber(number); // points to cached partition data... updating => update cache!
        pr.annotated = null;
        int year = partitionYearOf(pr);
        appendChange(year, new RemoveAnnotationPullRequestChangeRecord(number));
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
     */
    public void recompact(int year) {
        Map<Integer, GitHubPullRequestDTO> reloaded = readPartitionFromDisk(year);
        partitionCache.put(year, reloaded);
        writeSnapshot(year, reloaded.values());
        clearChangesFile(year);
    }

    public void recompactAll() {
        for (int year : findAllPartitionYears()) {
            recompact(year);
        }
    }

    private void clearChangesFile(int year) {
        try {
            Files.deleteIfExists(changesFile(year));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to clear changes log for " + partitionDirName(year), e);
        }
    }

    static int partitionYearOf(GitHubPullRequestDTO pr) {
        String created = pr.createdAt;
        if (created != null && created.length() >= 4) {
            try {
                return Integer.parseInt(created.substring(0, 4));
            } catch (NumberFormatException ignored) {
                // fall through to UNKNOWN_YEAR
            }
        }
        return UNKNOWN_YEAR;
    }

    private static int requireNumber(GitHubPullRequestDTO pr) {
        if (pr.number <= 0) {
            throw new IllegalArgumentException("pull request is missing its 'number' field");
        }
        return pr.number;
    }

    private Map<Integer, GitHubPullRequestDTO> cachedPartitionData(int year) {
        return partitionCache.computeIfAbsent(year, this::readPartitionFromDisk);
    }

    private Map<Integer, GitHubPullRequestDTO> readPartitionFromDisk(int year) {
        Map<Integer, GitHubPullRequestDTO> result = new LinkedHashMap<>();
        try {
            readSnapshot(year, result);
        } catch (Exception ex) {
            log.error("Failed to read pull request snapshot for partition year=" + year, ex);
        }
        replayChanges(year, result);
        return result;
    }

    private void readSnapshot(int year, Map<Integer, GitHubPullRequestDTO> target) {
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
                GitHubPullRequestDTO pr = mapper.readValue(line, GitHubPullRequestDTO.class);
                target.put(pr.number, pr);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read snapshot " + zipFile, e);
        }
    }

    public enum PullRequestChangeType {
        create,
        update,
        updateAnnotation,
        removeAnnotation
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "change")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = CreatePullRequestChangeRecord.class, name = "create"),
            @JsonSubTypes.Type(value = UpdatePullRequestChangeRecord.class, name = "update"),
            @JsonSubTypes.Type(value = UpdateAnnotationPullRequestChangeRecord.class, name = "updateAnnotation"),
            @JsonSubTypes.Type(value = RemoveAnnotationPullRequestChangeRecord.class, name = "removeAnnotation")
    })
    public static abstract class PullRequestChangeRecord {
        public abstract PullRequestChangeType getChange();
        public abstract int number();
    }

    @AllArgsConstructor
    public static class CreatePullRequestChangeRecord extends PullRequestChangeRecord {
        public GitHubPullRequestDTO data;

        @Override
        public PullRequestChangeType getChange() { return PullRequestChangeType.create; }

        @Override
        public int number() { return data.number; }
    }

    @AllArgsConstructor
    public static class UpdatePullRequestChangeRecord extends PullRequestChangeRecord {
        public GitHubPullRequestDTO data;

        @Override
        public PullRequestChangeType getChange() { return PullRequestChangeType.update; }

        @Override
        public int number() { return data.number; }
    }

    @AllArgsConstructor
    public static class UpdateAnnotationPullRequestChangeRecord extends PullRequestChangeRecord {
        public int number;
        public GitHubPullRequestExtraFieldsDTO annotated;

        @Override
        public PullRequestChangeType getChange() { return PullRequestChangeType.updateAnnotation; }

        @Override
        public int number() { return number; }
    }

    @AllArgsConstructor
    public static class RemoveAnnotationPullRequestChangeRecord extends PullRequestChangeRecord {
        public int number;

        @Override
        public PullRequestChangeType getChange() { return PullRequestChangeType.removeAnnotation; }

        @Override
        public int number() { return number; }
    }

    private void replayChanges(int year, Map<Integer, GitHubPullRequestDTO> prByNumber) {
        Path file = changesFile(year);
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                PullRequestChangeRecord rec = mapper.readValue(line, PullRequestChangeRecord.class);
                if (rec instanceof CreatePullRequestChangeRecord chg) {
                    prByNumber.put(chg.data.number, chg.data);
                } else if (rec instanceof UpdatePullRequestChangeRecord chg) {
                    prByNumber.put(chg.data.number, chg.data);
                } else if (rec instanceof UpdateAnnotationPullRequestChangeRecord chg) {
                    GitHubPullRequestDTO prev = prByNumber.get(chg.number);
                    if (prev != null) {
                        prev.annotated = chg.annotated;
                    } // else should not occur, ignore anyway
                } else if (rec instanceof RemoveAnnotationPullRequestChangeRecord chg) {
                    GitHubPullRequestDTO prev = prByNumber.get(chg.number);
                    if (prev != null) {
                        prev.annotated = null;
                    } // else should not occur, ignore anyway
                } else {
                    log.warn("unexpected change type " + rec.getChange() + " in file '" + file + "' ... ignore");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read changes log " + file, e);
        }
    }

    private void appendChange(int year, PullRequestChangeRecord chgRecord) {
        String line = mapper.writeValueAsString(chgRecord);
        try {
            Files.createDirectories(partitionDir(year));
            Files.writeString(changesFile(year), line + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append change for #" + chgRecord.number() + " in " + partitionDirName(year), e);
        }
    }

    private void writeSnapshot(int year, Collection<GitHubPullRequestDTO> prs) {
        Path dir = partitionDir(year);
        Path zipFile = snapshotFile(year);
        Path tmpFile = dir.resolve(SNAPSHOT_FILE + ".tmp");
        try {
            Files.createDirectories(dir);
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpFile), StandardCharsets.UTF_8)) {
                zos.putNextEntry(new ZipEntry(SNAPSHOT_ENTRY));
                Writer writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);
                for (GitHubPullRequestDTO pr : prs) {
                    writer.write(mapper.writeValueAsString(pr));
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
}
