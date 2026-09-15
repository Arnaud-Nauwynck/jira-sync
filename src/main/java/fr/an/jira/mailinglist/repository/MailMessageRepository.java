package fr.an.jira.mailinglist.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.an.jira.mailinglist.configuration.MailingListSyncProperties;
import fr.an.jira.mailinglist.rest.dtos.MailMessageDTO;
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
import java.time.YearMonth;
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
 * Reads, writes and queries the mailing-list messages persisted to disk, partitioned by the month
 * the message was sent in ("archived={yyyy-MM}" sub-directories, e.g. "archived=2026-01" — see
 * {@code src/test/data/mailing-list/archived=2026-01/}), each holding:
 * - "data.ndjson.zip": a single-entry zip of the compacted snapshot of all messages in the partition
 *   (one JSON object per line).
 * - "changes.ndjson": an append-only log of inserts/updates recorded since the last {@link #compact},
 *   one JSON object per line, shaped as {@code {"change":"create"|"update","data":<message>}}.
 * The effective state of a partition is the snapshot with the changes log replayed on top.
 * Messages with a missing/unparsable Date header fall into the {@link #UNKNOWN_MONTH} partition
 * ("archived=unknown").
 * <p>
 * Mirrors {@code fr.an.jira.github.repository.GitHubPullRequestRepository}'s on-disk format,
 * keyed by Message-ID instead of a PR number.
 */
@Component
@Slf4j
public class MailMessageRepository {

    static final String PARTITION_PREFIX = "archived=";
    static final String UNKNOWN_MONTH = "unknown";
    private static final String SNAPSHOT_FILE = "data.ndjson.zip";
    private static final String SNAPSHOT_ENTRY = "data.ndjson";
    private static final String CHANGES_FILE = "changes.ndjson";

    private final ObjectMapper mapper;

    private final Path baseDir;

    /** partition month ("yyyy-MM") -> (Message-ID -> current message), lazily loaded from disk. */
    private final Map<String, Map<String, MailMessageDTO>> partitionCache = new ConcurrentHashMap<>();

    @Autowired
    public MailMessageRepository(MailingListSyncProperties props, ObjectMapper mapper) throws IOException {
        this(Path.of(props.getMailingListSyncLocalDir()).resolve("messages"), mapper);
    }

    public MailMessageRepository(Path baseDir, ObjectMapper mapper) throws IOException {
        // dropping null-valued fields on write keeps the persisted ndjson files compact; the
        // injected mapper (used elsewhere, e.g. REST responses) is left untouched.
        this.mapper = mapper.rebuild()
                .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.ALL_NON_NULL)
                .build();
        this.baseDir = baseDir;
        if (!Files.exists(baseDir)) {
            log.warn("baseDir {} does not exist for mailing-list messages, creating", baseDir);
            Files.createDirectories(baseDir);
        }
    }

    /**
     * Maps the message to its persisted form and creates or updates it, recording a "create" or
     * "update" change in its partition.
     */
    public void savePartitionData(String partition, List<MailMessageDTO> partitionData) {
        if (partitionData.isEmpty()) {
            return;
        }
        Map<String, MailMessageDTO> cachedPartition = cachedPartitionData(partition);
        List<MailMessageChangeRecord> chgRecords = new ArrayList<>(partitionData.size());
        for (MailMessageDTO msg : partitionData) {
            String messageId = requireMessageId(msg);
            MailMessageDTO previous = cachedPartition.get(messageId);
            chgRecords.add((previous == null)
                    ? new CreateMailMessageChangeRecord(msg)
                    : new UpdateMailMessageChangeRecord(msg));
        }
        appendChanges(partition, chgRecords);
        for (MailMessageDTO msg : partitionData) {
            cachedPartition.put(msg.messageId, msg);
        }
    }

    /** should be used only for incremental synchronization within month, otherwise see savePartitionData(partition, partitionData) */
    public void save(MailMessageDTO msg) {
        String messageId = requireMessageId(msg);
        String partition = partitionMonthOf(msg);
        Map<String, MailMessageDTO> cachedPartition = cachedPartitionData(partition);
        MailMessageDTO previous = cachedPartition.get(messageId);
        MailMessageChangeRecord chgRecord = (previous == null)
                ? new CreateMailMessageChangeRecord(msg)
                : new UpdateMailMessageChangeRecord(msg);
        appendChange(partition, chgRecord);
        cachedPartition.put(messageId, msg);
    }

    public boolean exists(String messageId) {
        return findByMessageId(messageId) != null;
    }

    /** Reads a single message by Message-ID across all partitions, or null if not found. */
    public MailMessageDTO findByMessageId(String messageId) {
        for (String month : findAllPartitionMonths()) {
            MailMessageDTO found = cachedPartitionData(month).get(messageId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** get by Message-ID, throws exception if not found. */
    @Nonnull
    public MailMessageDTO getByMessageId(String messageId) {
        MailMessageDTO found = findByMessageId(messageId);
        if (found == null) {
            throw new IllegalArgumentException("Mail message " + messageId + " not found");
        }
        return found;
    }

    /** Lists the Message-IDs of all messages currently stored on disk. */
    public List<String> findAllMessageIds() {
        List<String> ids = new ArrayList<>();
        for (String month : findAllPartitionMonths()) {
            ids.addAll(cachedPartitionData(month).keySet());
        }
        return ids;
    }

    /** Reads all messages currently stored on disk, across all partitions. */
    public List<MailMessageDTO> findAll() {
        List<MailMessageDTO> all = new ArrayList<>();
        for (String month : findAllPartitionMonths()) {
            all.addAll(cachedPartitionData(month).values());
        }
        return all;
    }

    /** Lists the months ("yyyy-MM", or "unknown") of the partitions currently present on disk, sorted. */
    public List<String> findAllPartitionMonths() {
        if (!Files.exists(baseDir)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(baseDir)) {
            return dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(PARTITION_PREFIX))
                    .map(name -> name.substring(PARTITION_PREFIX.length()))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list " + baseDir, e);
        }
    }

    /** Streams every message whose partition month falls within [fromMonth, toMonth] to the callback. */
    public void scanMessages(String fromMonth, String toMonth, BiConsumer<String, MailMessageDTO> callback) {
        for (String month : findPartitionMonthsBetween(fromMonth, toMonth)) {
            for (MailMessageDTO msg : cachedPartitionData(month).values()) {
                callback.accept(month, msg);
            }
        }
    }

    /** Lists the partition months within [fromMonth, toMonth] (both "yyyy-MM", either bound optional) that exist on disk. */
    public List<String> findPartitionMonthsBetween(String fromMonth, String toMonth) {
        List<String> res = new ArrayList<>();
        for (String month : findAllPartitionMonths()) {
            if (UNKNOWN_MONTH.equals(month)) {
                continue;
            }
            if ((fromMonth == null || month.compareTo(fromMonth) >= 0)
                    && (toMonth == null || month.compareTo(toMonth) <= 0)) {
                res.add(month);
            }
        }
        return res;
    }

    /** Folds the pending changes log into a fresh compacted snapshot, then clears the changes log. */
    public void compact(String month) {
        writeSnapshot(month, cachedPartitionData(month).values());
        clearChangesFile(month);
    }

    public void compactAll() {
        for (String month : findAllPartitionMonths()) {
            compact(month);
        }
    }

    /**
     * Discards any cached in-memory state for the partition, reloads it from disk (snapshot +
     * replayed changes), then rewrites the compacted snapshot and clears the changes log.
     */
    public void recompact(String month) {
        Map<String, MailMessageDTO> reloaded = readPartitionFromDisk(month);
        partitionCache.put(month, reloaded);
        writeSnapshot(month, reloaded.values());
        clearChangesFile(month);
    }

    public void recompactAll() {
        for (String month : findAllPartitionMonths()) {
            recompact(month);
        }
    }

    private void clearChangesFile(String month) {
        try {
            Files.deleteIfExists(changesFile(month));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to clear changes log for " + partitionDirName(month), e);
        }
    }

    static String partitionMonthOf(MailMessageDTO msg) {
        return (msg.date != null) ? YearMonth.from(msg.date).toString() : UNKNOWN_MONTH;
    }

    private static String requireMessageId(MailMessageDTO msg) {
        String messageId = msg.messageId;
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("mail message is missing its 'messageId' field");
        }
        return messageId;
    }

    private Map<String, MailMessageDTO> cachedPartitionData(String month) {
        return partitionCache.computeIfAbsent(month, this::readPartitionFromDisk);
    }

    private Map<String, MailMessageDTO> readPartitionFromDisk(String month) {
        Map<String, MailMessageDTO> result = new LinkedHashMap<>();
        try {
            readSnapshot(month, result);
        } catch (Exception ex) {
            log.error("Failed to read mail message snapshot for partition month=" + month, ex);
        }
        replayChanges(month, result);
        return result;
    }

    private void readSnapshot(String month, Map<String, MailMessageDTO> target) {
        Path zipFile = snapshotFile(month);
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
                MailMessageDTO msg = mapper.readValue(line, MailMessageDTO.class);
                target.put(msg.messageId, msg);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read snapshot " + zipFile, e);
        }
    }

    public enum MailMessageChangeType {
        create,
        update
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "change")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = CreateMailMessageChangeRecord.class, name = "create"),
            @JsonSubTypes.Type(value = UpdateMailMessageChangeRecord.class, name = "update")
    })
    public static abstract class MailMessageChangeRecord {
        public abstract MailMessageChangeType getChange();
        public abstract String messageId();
    }

    @AllArgsConstructor
    public static class CreateMailMessageChangeRecord extends MailMessageChangeRecord {
        public MailMessageDTO data;

        @Override
        public MailMessageChangeType getChange() { return MailMessageChangeType.create; }

        @Override
        public String messageId() { return data.messageId; }
    }

    @AllArgsConstructor
    public static class UpdateMailMessageChangeRecord extends MailMessageChangeRecord {
        public MailMessageDTO data;

        @Override
        public MailMessageChangeType getChange() { return MailMessageChangeType.update; }

        @Override
        public String messageId() { return data.messageId; }
    }

    private void replayChanges(String month, Map<String, MailMessageDTO> msgByMessageId) {
        Path file = changesFile(month);
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                MailMessageChangeRecord rec = mapper.readValue(line, MailMessageChangeRecord.class);
                if (rec instanceof CreateMailMessageChangeRecord chg) {
                    msgByMessageId.put(chg.data.messageId, chg.data);
                } else if (rec instanceof UpdateMailMessageChangeRecord chg) {
                    msgByMessageId.put(chg.data.messageId, chg.data);
                } else {
                    log.warn("unexpected change type " + rec.getChange() + " in file '" + file + "' ... ignore");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read changes log " + file, e);
        }
    }

    private void appendChange(String month, MailMessageChangeRecord chgRecord) {
        appendChanges(month, List.of(chgRecord));
    }

    private void appendChanges(String month, Collection<MailMessageChangeRecord> chgRecords) {
        StringBuilder sb = new StringBuilder();
        for (MailMessageChangeRecord chgRecord : chgRecords) {
            sb.append(mapper.writeValueAsString(chgRecord)).append("\n");
        }
        try {
            Files.createDirectories(partitionDir(month));
            Files.writeString(changesFile(month), sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append changes in " + partitionDirName(month), e);
        }
    }

    private void writeSnapshot(String month, Collection<MailMessageDTO> messages) {
        Path dir = partitionDir(month);
        Path zipFile = snapshotFile(month);
        Path tmpFile = dir.resolve(SNAPSHOT_FILE + ".tmp");
        try {
            Files.createDirectories(dir);
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpFile), StandardCharsets.UTF_8)) {
                zos.putNextEntry(new ZipEntry(SNAPSHOT_ENTRY));
                Writer writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);
                for (MailMessageDTO msg : messages) {
                    writer.write(mapper.writeValueAsString(msg));
                    writer.write("\n");
                }
                writer.flush();
                zos.closeEntry();
            }
            Files.move(tmpFile, zipFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write snapshot for " + partitionDirName(month), e);
        }
    }

    private Path partitionDir(String month) {
        return baseDir.resolve(partitionDirName(month));
    }

    private Path snapshotFile(String month) {
        return partitionDir(month).resolve(SNAPSHOT_FILE);
    }

    private Path changesFile(String month) {
        return partitionDir(month).resolve(CHANGES_FILE);
    }

    static String partitionDirName(String month) {
        return PARTITION_PREFIX + month;
    }

}
