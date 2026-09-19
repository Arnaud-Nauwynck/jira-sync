package fr.an.projectanalysis.github.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubPullRequestDTO;
import fr.an.projectanalysis.github.configuration.GitHubSyncProperties;
import fr.an.projectanalysis.github.mapper.SourceGitHubToAnnotatedPullRequestMapper;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestExtraFieldsDTO;
import fr.an.projectanalysis.github.rest.dtos.YearCountDTO;
import fr.an.projectanalysis.service.GithubPRChange;
import fr.an.projectanalysis.service.RecentChangeLogService;
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
import java.util.function.Consumer;
import java.util.function.Predicate;
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
 */
@Component
@Slf4j
public class GitHubPullRequestRepository {

    static final String PARTITION_PREFIX = "created_year=";
    static final int UNKNOWN_YEAR = -1;
    private static final String SNAPSHOT_FILE = "data.ndjson.zip";
    private static final String SNAPSHOT_ENTRY = "data.ndjson";
    private static final String CHANGES_FILE = "changes.ndjson";
    private static final String STATS_FILE = "data-stats.json";

    private final ObjectMapper mapper;

    private final Path baseDir;

    /** Not null when Spring-managed; null when constructed directly by a one-off migration tool,
     * which should not feed the live "recent activity" feed. */
    private final RecentChangeLogService changeLogService;

    private final Object changeFileLock = new Object();

    /** partition year -> (PR number -> current PR), lazily loaded from disk and kept up to date. */
    private final Map<Integer, Map<Integer, GitHubPullRequestDTO>> partitionCache = new ConcurrentHashMap<>();

    /** partition year -> PR count/min/max number, loaded from {@value #STATS_FILE} (or recomputed if missing)
     * on first access, kept up to date incrementally as PRs are created, and re-persisted on compact/recompact. */
    private final Map<Integer, PartitionIndexes> partitionStats = new ConcurrentHashMap<>();
    private volatile boolean partitionStatsLoaded = false;

    /** Count, and lowest/highest PR number, of the PRs in a single partition. PR numbers are sequential, so
     * min/max give a cheap sense of the partition's covered range without listing every PR. */
    public static class PartitionIndexes {
        public int count;
        public Integer minId;
        public Integer maxId;

        void addId(int id) {
            count++;
            if (minId == null || id < minId) {
                minId = id;
            }
            if (maxId == null || id > maxId) {
                maxId = id;
            }
        }

        public YearCountDTO toDTO(int year) {
            return new YearCountDTO(year, count, minId, maxId);
        }
    }

    private static class StatsFile {
        public Map<Integer, PartitionIndexes> partitionStats = new LinkedHashMap<>();
    }

    @Autowired
    public GitHubPullRequestRepository(GitHubSyncProperties props, ObjectMapper mapper, RecentChangeLogService changeLogService) throws IOException {
        this(Path.of(props.getGithubSyncLocalDir()).resolve("pulls"), mapper, changeLogService);
    }

    public GitHubPullRequestRepository(Path baseDir, ObjectMapper mapper) throws IOException {
        this(baseDir, mapper, null);
    }

    public GitHubPullRequestRepository(Path baseDir, ObjectMapper mapper, RecentChangeLogService changeLogService) throws IOException {
        // dropping null-valued fields on write keeps the persisted ndjson files compact; the
        // injected mapper (used elsewhere, e.g. REST responses) is left untouched.
        this.mapper = mapper.rebuild()
                .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.ALL_NON_NULL)
                .build();
        this.baseDir = baseDir;
        this.changeLogService = changeLogService;
        if (!Files.exists(baseDir)) {
            log.warn("baseDir {} does not exist for github pulls, creating", baseDir);
            Files.createDirectories(baseDir);
        }
    }

    private void fireChange(int number, String changeType) {
        if (changeLogService != null) {
            changeLogService.addEvent(new GithubPRChange(number, changeType));
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
            ensurePartitionStatsLoaded();
            partitionStats.computeIfAbsent(year, y -> new PartitionIndexes()).addId(number);
            fireChange(number, "create");
        } else {
            pr.annotated = previous.annotated;
            chgRecord = new UpdatePullRequestChangeRecord(pr);
            fireChange(number, "update");
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

    /** Lists the PRs in a single "created_year" partition matching the given filter. */
    public List<GitHubPullRequestDTO> findByPartitionYear(int year, Predicate<GitHubPullRequestDTO> filter) {
        List<GitHubPullRequestDTO> result = new ArrayList<>();
        for (GitHubPullRequestDTO pr : cachedPartitionData(year).values()) {
            if (filter == null || filter.test(pr)) {
                result.add(pr);
            }
        }
        return result;
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

    /** Count, and lowest/highest PR number, per "created_year" partition, from the in-memory stats
     * (see {@link #STATS_FILE}). */
    public Map<Integer, PartitionIndexes> partitionStats() {
        ensurePartitionStatsLoaded();
        return new LinkedHashMap<>(partitionStats);
    }

    /** Loads {@link #partitionStats} from {@value #STATS_FILE} on first call, or recomputes and persists it
     * from the partitions on disk when the file is missing/unreadable. */
    private synchronized void ensurePartitionStatsLoaded() {
        if (partitionStatsLoaded) {
            return;
        }
        Path statsFile = baseDir.resolve(STATS_FILE);
        if (Files.exists(statsFile)) {
            try {
                String json = Files.readString(statsFile, StandardCharsets.UTF_8);
                StatsFile loaded = mapper.readValue(json, StatsFile.class);
                if (loaded.partitionStats != null) {
                    partitionStats.putAll(loaded.partitionStats);
                }
                partitionStatsLoaded = true;
                return;
            } catch (Exception e) {
                log.warn("failed to read {}, recomputing from partitions", statsFile, e);
            }
        }
        recomputeAllPartitionStats();
        partitionStatsLoaded = true;
        writePartitionStatsFile();
    }

    private void recomputeAllPartitionStats() {
        for (int year : findAllPartitionYears()) {
            partitionStats.put(year, recomputePartitionStats(year));
        }
    }

    private PartitionIndexes recomputePartitionStats(int year) {
        PartitionIndexes stats = new PartitionIndexes();
        for (GitHubPullRequestDTO pr : cachedPartitionData(year).values()) {
            stats.addId(pr.number);
        }
        return stats;
    }

    private void writePartitionStatsFile() {
        try {
            Path statsFile = baseDir.resolve(STATS_FILE);
            StatsFile toWrite = new StatsFile();
            toWrite.partitionStats = new java.util.TreeMap<>(partitionStats);
            String json = mapper.writeValueAsString(toWrite);
            Files.writeString(statsFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + STATS_FILE, e);
        }
    }

    public void mutateIssue(int number, Consumer<GitHubPullRequestDTO> updateCallback) {
        GitHubPullRequestDTO pr = getByNumber(number); // points to cached partition data... updating => update cache!
        updateCallback.accept(pr);
        int year = partitionYearOf(pr);
        appendChange(year, new UpdatePullRequestChangeRecord(pr));
        fireChange(number, "update");
    }


    /** Folds the pending changes log into a fresh compacted snapshot, then clears the changes log
     * and re-persists the partition stats (see {@value #STATS_FILE}). */
    public void compact(int year) {
        writeSnapshot(year, cachedPartitionData(year).values());
        clearChangesFile(year);
        ensurePartitionStatsLoaded();
        partitionStats.put(year, recomputePartitionStats(year));
        writePartitionStatsFile();
    }

    public void compactAll() {
        for (int year : findAllPartitionYears()) {
            compact(year);
        }
    }

    /**
     * Discards any cached in-memory state for the partition, reloads it from disk (snapshot +
     * replayed changes), then rewrites the compacted snapshot, clears the changes log, and
     * re-persists the partition stats (see {@value #STATS_FILE}).
     */
    public void recompact(int year) {
        Map<Integer, GitHubPullRequestDTO> reloaded = readPartitionFromDisk(year);
        partitionCache.put(year, reloaded);
        writeSnapshot(year, reloaded.values());
        clearChangesFile(year);
        ensurePartitionStatsLoaded();
        PartitionIndexes stats = new PartitionIndexes();
        for (GitHubPullRequestDTO pr : reloaded.values()) {
            stats.addId(pr.number);
        }
        partitionStats.put(year, stats);
        writePartitionStatsFile();
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
                if (line.isBlank()) {
                    continue;
                }
                PullRequestChangeRecord rec;
                try {
                    rec = mapper.readValue(line, PullRequestChangeRecord.class);
                } catch(Exception ex) {
                    log.error("FATAL ... failed to reload change log for PullRequest, ignore, no rethrow!! line:\n" + line + "\n", ex);
                    continue; // ignore no rethrow!
                }
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
        String appendText = "\n" + mapper.writeValueAsString(chgRecord) + "\n";
        try {
            Files.createDirectories(partitionDir(year));
            synchronized (changeFileLock) {
                Files.writeString(changesFile(year), appendText, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
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
