package fr.an.projectanalysis.claude.repository;

import fr.an.projectanalysis.claude.configuration.ClaudeProperties;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptBatchDTO;
import fr.an.projectanalysis.util.YearPartitionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists finished {@code claude} CLI prompt invocations ({@link ClaudeCodePromptBatchDTO}) as an
 * append-only ndjson log, partitioned by the year the prompt started in ("created_year={yyyy}"
 * sub-directories, one json line per finished batch in "changes.ndjson" — see
 * {@link YearPartitionUtils}). Unlike {@code JiraIssueRepository}/{@code MailMessageRepository},
 * entries are immutable once written, so there is no snapshot/compaction step: each partition's
 * log file is the whole state.
 */
@Component
@Slf4j
public class ClaudeCodePromptBatchRepository {

    private final ObjectMapper mapper;

    private final Path baseDir;

    private final Object changeFileLock = new Object();

    @Autowired
    public ClaudeCodePromptBatchRepository(ClaudeProperties props, ObjectMapper mapper) throws IOException {
        this(Path.of(props.getPromptBatchLocalDir()), mapper);
    }

    public ClaudeCodePromptBatchRepository(Path baseDir, ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        this.baseDir = baseDir;
        if (!Files.exists(baseDir)) {
            log.warn("baseDir {} does not exist for claude prompt batches, creating", baseDir);
            Files.createDirectories(baseDir);
        }
    }

    public void save(ClaudeCodePromptBatchDTO batch) {
        int year = partitionYearOf(batch);
        YearPartitionUtils.appendChangeLine(mapper, baseDir, year, batch, changeFileLock,
                "prompt started at " + batch.startTime);
    }

    /** Reads all finished prompt batches, across all partitions, oldest partitions first. */
    public List<ClaudeCodePromptBatchDTO> findAll() {
        List<ClaudeCodePromptBatchDTO> result = new ArrayList<>();
        for (int year : findAllPartitionYears()) {
            result.addAll(findByYear(year));
        }
        return result;
    }

    /** Reads the finished prompt batches persisted for a single partition year. */
    public List<ClaudeCodePromptBatchDTO> findByYear(int year) {
        Path file = YearPartitionUtils.changesFile(baseDir, year);
        if (!Files.exists(file)) {
            return List.of();
        }
        List<ClaudeCodePromptBatchDTO> result = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                result.add(mapper.readValue(line, ClaudeCodePromptBatchDTO.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
        return result;
    }

    /** Lists the years of the partitions currently present on disk. */
    public List<Integer> findAllPartitionYears() {
        return YearPartitionUtils.findAllPartitionYears(baseDir);
    }

    private static int partitionYearOf(ClaudeCodePromptBatchDTO batch) {
        return Instant.ofEpochMilli(batch.startTime).atZone(ZoneId.systemDefault()).getYear();
    }

}
