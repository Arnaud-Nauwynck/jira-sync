package fr.an.projectanalysis.util;

import org.slf4j.Logger;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * On-disk layout and (de)serialization of the repositories partitioned by creation year into
 * "created_year={yyyy}" sub-directories, each holding a zipped ndjson snapshot plus an append-only
 * changes log (shared by the Jira issue and the GitHub pull request repositories, which differ only
 * by the record type and by how a record's id is extracted).
 */
public class YearPartitionUtils {

    public static final String PARTITION_PREFIX = "created_year=";

    /** Partition of the records with a missing/unparsable creation date ("created_year=unknown"). */
    public static final int UNKNOWN_YEAR = -1;

    public static final String SNAPSHOT_FILE = "data.ndjson.zip";

    public static final String SNAPSHOT_ENTRY = "data.ndjson";

    public static final String CHANGES_FILE = "changes.ndjson";

    public static final String STATS_FILE = "data-stats.json";

    private YearPartitionUtils() {
    }

    public static String partitionDirName(int year) {
        return year == UNKNOWN_YEAR ? PARTITION_PREFIX + "unknown" : PARTITION_PREFIX + year;
    }

    public static int parseYear(String partitionDirName) {
        try {
            return Integer.parseInt(partitionDirName.substring(PARTITION_PREFIX.length()));
        } catch (NumberFormatException e) {
            return UNKNOWN_YEAR;
        }
    }

    public static Path partitionDir(Path baseDir, int year) {
        return baseDir.resolve(partitionDirName(year));
    }

    public static Path snapshotFile(Path baseDir, int year) {
        return partitionDir(baseDir, year).resolve(SNAPSHOT_FILE);
    }

    public static Path changesFile(Path baseDir, int year) {
        return partitionDir(baseDir, year).resolve(CHANGES_FILE);
    }

    /** Lists the years of the "created_year=yyyy" partitions currently present on disk. */
    public static List<Integer> findAllPartitionYears(Path baseDir) {
        if (!Files.exists(baseDir)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(baseDir)) {
            return dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(PARTITION_PREFIX))
                    .map(YearPartitionUtils::parseYear)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list " + baseDir, e);
        }
    }

    /** Lists the partition years within [fromYear, toYear] that currently exist on disk. */
    public static List<Integer> findPartitionYearBetween(Path baseDir, int fromYear, int toYear) {
        List<Integer> res = new ArrayList<>();
        for (int year : findAllPartitionYears(baseDir)) {
            if (year >= fromYear && year <= toYear) {
                res.add(year);
            }
        }
        return res;
    }

    /** Reads the partition's zipped ndjson snapshot (one record per line) into {@code target}, keyed by
     * {@code keyOf}; a missing snapshot file leaves {@code target} untouched. */
    public static <K, V> void readSnapshot(ObjectMapper mapper, Path baseDir, int year,
            Class<V> recordClass, Function<V, K> keyOf, Map<K, V> target) {
        Path zipFile = snapshotFile(baseDir, year);
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
                V record = mapper.readValue(line, recordClass);
                target.put(keyOf.apply(record), record);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read snapshot " + zipFile, e);
        }
    }

    /** Writes the partition's zipped ndjson snapshot (one record per line), atomically replacing the
     * previous one via a ".tmp" file. */
    public static void writeSnapshot(ObjectMapper mapper, Path baseDir, int year, Collection<?> records) {
        Path dir = partitionDir(baseDir, year);
        Path zipFile = dir.resolve(SNAPSHOT_FILE);
        Path tmpFile = dir.resolve(SNAPSHOT_FILE + ".tmp");
        try {
            Files.createDirectories(dir);
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpFile), StandardCharsets.UTF_8)) {
                zos.putNextEntry(new ZipEntry(SNAPSHOT_ENTRY));
                Writer writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);
                for (Object record : records) {
                    writer.write(mapper.writeValueAsString(record));
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

    /** Appends one change record as a json line to the partition's changes log, under {@code changeFileLock}.
     * {@code recordLabel} only identifies the record in the failure message (eg "#1234" or "PROJ-123"). */
    public static void appendChangeLine(ObjectMapper mapper, Path baseDir, int year,
            Object changeRecord, Object changeFileLock, String recordLabel) {
        String appendText = "\n" + mapper.writeValueAsString(changeRecord) + "\n";
        try {
            Files.createDirectories(partitionDir(baseDir, year));
            synchronized (changeFileLock) {
                Files.writeString(changesFile(baseDir, year), appendText,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append change for " + recordLabel
                    + " in " + partitionDirName(year), e);
        }
    }

    /** Reads a json file (typically {@value #STATS_FILE}), or returns null when it is missing or unreadable,
     * so the caller can recompute it from the partitions instead of failing. */
    public static <T> T readJsonFileOrNull(ObjectMapper mapper, Path jsonFile, Class<T> type, Logger log) {
        if (!Files.exists(jsonFile)) {
            return null;
        }
        try {
            String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("failed to read {}, recomputing from partitions", jsonFile, e);
            return null;
        }
    }

}
