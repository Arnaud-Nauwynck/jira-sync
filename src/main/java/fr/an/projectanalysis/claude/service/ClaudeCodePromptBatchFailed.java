package fr.an.projectanalysis.claude.service;

import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptBatchDTO;
import fr.an.projectanalysis.service.ChangeLogEvent;
import lombok.Getter;
import lombok.val;

import java.time.Duration;
import java.time.LocalDateTime;

/** A recorded start/success/error of a {@code claude} CLI prompt invocation, keyed by its pid. */
@Getter
public class ClaudeCodePromptBatchFailed extends ChangeLogEvent {

    /** id of to the running batch -> infos: startTime, prompt, pid, ... */
    private final long runningBatchId;

    private final int exitCode;

    private final String output;

    // repeat info from start
    private final LocalDateTime startTimestamp;
    private final String prompt;

    public ClaudeCodePromptBatchFailed(
            LocalDateTime timestamp,
            long runningBatchId,
            int exitCode,
            String output,
            // repeat info from start
            LocalDateTime startTimestamp,
            String prompt
            ) {
        super(timestamp);
        this.runningBatchId = runningBatchId;
        this.exitCode = exitCode;
        this.output = output;
        this.startTimestamp = startTimestamp;
        this.prompt = prompt;
    }

    @Override
    public String summary() {
        val elapsedSeconds = Duration.between(startTimestamp, timestamp).toSeconds();
        return "Claude prompt Failed runningBatchId=" + runningBatchId + " after " + elapsedSeconds + "s, "
                + "prompt: " + prompt
                + " => exitCode:" + exitCode + " output: " + output;
    }

}
