package fr.an.projectanalysis.claude.service;

import lombok.Getter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** A running (or just-finished) invocation of the {@code claude} CLI, tracked by
 * {@link ClaudeCodePromptInvokerService} while its process is alive. */
@Getter
public class ClaudeCodePromptRunningBatch {

    /** internal PK for tracking running batches */
    private final long runningBatchId;

    private final String prompt;
    private final List<String> allowedTools;
    private final long startTime;
    private final long pid;

    /** completed (never exceptionally) once the process has ended and its result been handled, whatever the outcome */
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    public ClaudeCodePromptRunningBatch(long runningBatchId, String prompt, List<String> allowedTools, long startTime, long pid) {
        this.runningBatchId = runningBatchId;
        this.prompt = prompt;
        this.allowedTools = allowedTools;
        this.startTime = startTime;
        this.pid = pid;
    }

}
