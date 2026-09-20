package fr.an.projectanalysis.claude.service;

import fr.an.projectanalysis.claude.configuration.ClaudeProperties;
import fr.an.projectanalysis.claude.repository.ClaudeCodePromptBatchRepository;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodeBatchCriteriaDTO;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptBatchDTO;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptRunningBatchDTO;
import fr.an.projectanalysis.claude.service.dto.SourceClaudeCodePromptBatchResponseDTO;
import fr.an.projectanalysis.service.RecentChangeLogService;
import fr.an.projectanalysis.util.LsUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Invokes the local {@code claude} CLI (Claude Code) with a one-shot prompt, and returns its
 * {@code --output-format json} response text. */
@Component
@Slf4j
public class ClaudeCodePromptInvokerService {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private final ClaudeProperties props;

    private final RecentChangeLogService changeLogService;

    private final ClaudeCodePromptBatchRepository batchRepository;

    private final ObjectMapper mapper;

    private final Object lock = new Object();
    private final Map<Long, ClaudeCodePromptRunningBatch> currentClaudeCodePromptCall = new ConcurrentHashMap<>();

    /** Dedicated pool for consuming a launched Claude CLI process's output and awaiting its
     * completion, so {@link #invokePrompt(String, List, boolean)} can return the runningBatchId
     * as soon as the process is launched. */
    private final ExecutorService promptCompletionExecutor = Executors.newCachedThreadPool();

    public ClaudeCodePromptInvokerService(ClaudeProperties props, RecentChangeLogService changeLogService,
            ClaudeCodePromptBatchRepository batchRepository, ObjectMapper mapper) {
        this.props = props;
        this.changeLogService = changeLogService;
        this.batchRepository = batchRepository;
        this.mapper = mapper;
    }

    /** @return runningBatchId */
    public ClaudeCodePromptRunningBatch invokePrompt(String prompt) throws IOException {
        return invokePrompt(prompt, List.of());
    }

    /** @return the currently running (not yet completed) claude CLI prompt invocations. */
    public List<ClaudeCodePromptRunningBatchDTO> getCurrentRunningBatchDTOs() {
        synchronized (lock) {
            return LsUtil.map(currentClaudeCodePromptCall.values(), x -> toDTO(x));
        }
    }

    /** @return all finished Claude CLI prompt invocations persisted so far, across all partition years. */
    public List<ClaudeCodePromptBatchDTO> getPromptBatches() {
        return batchRepository.findAll();
    }

    /** @return the finished Claude CLI prompt invocations persisted for a single partition year. */
    public List<ClaudeCodePromptBatchDTO> getPromptBatches(int year) {
        return batchRepository.findByYear(year);
    }

    /** @return the partition years having at least one persisted finished prompt invocation. */
    public List<Integer> getPromptBatchYears() {
        return batchRepository.findAllPartitionYears();
    }

    /** @return the finished Claude CLI prompt invocations matching the given criteria (a null criteria
     * returns all of them, same as {@link #getPromptBatches()}). */
    public List<ClaudeCodePromptBatchDTO> queryPromptBatches(ClaudeCodeBatchCriteriaDTO criteria) {
        ClaudeCodeBatchCriteria predicate = new ClaudeCodeBatchCriteria(criteria);
        return batchRepository.findAll().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    private static ClaudeCodePromptRunningBatchDTO toDTO(ClaudeCodePromptRunningBatch src) {
        return new ClaudeCodePromptRunningBatchDTO(src.getRunningBatchId(), src.getPrompt(), src.getAllowedTools(), src.getStartTime(), src.getPid());
    }

    /** @param allowedTools MCP/built-in tool names to pre-approve (via {@code --allowedTools}), so the
     * headless CLI does not block on a permission prompt it has no TTY to show; empty means none pre-approved. */
    public ClaudeCodePromptRunningBatch invokePrompt(String prompt, List<String> allowedTools) throws IOException {
        return invokePrompt(prompt, allowedTools, false);
    }

    /** Launches the Claude CLI process and returns immediately with its runningBatchId; consuming
     * the process output and awaiting its completion happens asynchronously on {@link #promptCompletionExecutor}.
     * @param verboseOutput when true, the output logged on completion is pretty-printed as JSON
     * (falling back to the raw text if it does not parse as JSON); when false, it is logged as-is.
     * @return runningBatchId
     */
    public ClaudeCodePromptRunningBatch invokePrompt(String prompt, List<String> allowedTools, boolean verboseOutput) throws IOException {
        long startTime = System.currentTimeMillis();
        LocalDateTime startTimestamp = LocalDateTime.now();
        List<String> command = new ArrayList<>(List.of("claude", "-p", prompt, "--output-format", "json"));
        if (!allowedTools.isEmpty()) {
            command.add("--allowedTools");
            command.add(String.join(",", allowedTools));
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        if (props.getWorkingDir() != null && !props.getWorkingDir().isBlank()) {
            pb.directory(new File(props.getWorkingDir()));
        }
        // cf ProcessBuilder.Redirect.NULL_FILE (but private)
        pb.redirectInput(ProcessBuilder.Redirect.from(new File((IS_WINDOWS ? "NUL" : "/dev/null"))));
        pb.redirectErrorStream(false);
        Process process = pb.start();
        long pid = process.pid();
        long runningBatchId = newRunningBatchId();
        ClaudeCodePromptRunningBatch resRunningBatch = new ClaudeCodePromptRunningBatch(runningBatchId, prompt, allowedTools, startTime, pid);
        synchronized (lock) {
            currentClaudeCodePromptCall.put(runningBatchId, resRunningBatch);
        }
        log.info("Launched Claude CLI process runningBatchId={} pid={}, prompt=\"{}\"", runningBatchId, pid, prompt);
        changeLogService.addEvent(new ClaudeCodePromptBatchStart(startTimestamp, runningBatchId, prompt, pid));

        promptCompletionExecutor.submit(() -> awaitPromptCompletion(
                process, runningBatchId, pid, prompt, startTime, startTimestamp, verboseOutput));

        return resRunningBatch;
    }

    /** Consumes the process output (until closed), waits for it to exit, then persists+logs the
     * result; run on {@link #promptCompletionExecutor} so {@code invokePrompt} does not block on it. */
    private void awaitPromptCompletion(Process process, long runningBatchId, long pid, String prompt,
            long startTime, LocalDateTime startTimestamp, boolean verboseOutput) {
        try {
            // *** The Biggy : consume all output (until closed), and wait end of process ***
            String outputText = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            long endTime = System.currentTimeMillis();
            LocalDateTime endTimestamp = LocalDateTime.now();

            if (exitCode != 0) {
                log.error("Claude CLI process pid={} failed with exit code {}, output: \n{}", pid, exitCode, formatOutput(outputText, verboseOutput));
                changeLogService.addEvent(new ClaudeCodePromptBatchFailed(endTimestamp, runningBatchId, //
                        exitCode, outputText, startTimestamp, prompt));
                return;
            }

            val parsedBatchResponseDTO = mapper.readValue(outputText, SourceClaudeCodePromptBatchResponseDTO.class);

            ClaudeCodePromptBatchDTO batchDTO = ClaudeCodePromptBatchDTOMapper.toDTO(startTime, prompt, endTime, parsedBatchResponseDTO);
            batchRepository.save(batchDTO);

            log.info("Claude CLI process pid={} succeeded, output: {}", pid, formatOutput(outputText, verboseOutput));
            changeLogService.addEvent(new ClaudeCodePromptBatchEnd(endTimestamp, runningBatchId, batchDTO));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Claude CLI process pid={} runningBatchId={} failed", pid, runningBatchId, ex);
        } finally {
            synchronized (lock) {
                currentClaudeCodePromptCall.remove(runningBatchId);
            }
        }
    }

    @PreDestroy
    private void shutdownPromptCompletionExecutor() {
        promptCompletionExecutor.shutdown();
        try {
            if (!promptCompletionExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                promptCompletionExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            promptCompletionExecutor.shutdownNow();
        }
    }

    /** Generates a unique call id, using the current time in millis and disambiguating against
     * ids already tracked in {@link #currentClaudeCodePromptCall} in case two calls start within
     * the same millisecond. */
    private long newRunningBatchId() {
        long id = System.currentTimeMillis();
        synchronized (lock) {
            while (currentClaudeCodePromptCall.containsKey(id)) {
                id++;
            }
        }
        return id;
    }

    /** Pretty-prints {@code output} as JSON when {@code verboseOutput}, falling back to the raw text
     * when it does not parse as JSON; returns {@code output} unchanged when not verbose. */
    private String formatOutput(String output, boolean verboseOutput) {
        if (!verboseOutput) {
            return output;
        }
        try {
            Object json = mapper.readValue(output, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception ex) {
            return output;
        }
    }

}
