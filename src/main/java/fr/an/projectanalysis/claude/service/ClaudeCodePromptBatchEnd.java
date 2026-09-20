package fr.an.projectanalysis.claude.service;

import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptBatchDTO;
import fr.an.projectanalysis.service.ChangeLogEvent;
import lombok.Getter;

import java.time.LocalDateTime;

/** A recorded start/success/error of a {@code claude} CLI prompt invocation, keyed by its pid. */
@Getter
public class ClaudeCodePromptBatchEnd extends ChangeLogEvent {

    /** id of to the running batch -> infos: startTime, prompt, pid, ... */
    private final long runningBatchId;

    private final ClaudeCodePromptBatchDTO batch;

    public ClaudeCodePromptBatchEnd(
            LocalDateTime timestamp,
            long runningBatchId,
            ClaudeCodePromptBatchDTO batch
            ) {
        super(timestamp);
        this.runningBatchId = runningBatchId;
        this.batch = batch;

    }

    @Override
    public String summary() {
        return "Claude prompt End runningBatchId=" + runningBatchId + " took " + batch.elapsedSeconds + "s, output result: " + batch.outputResult;
    }

}
