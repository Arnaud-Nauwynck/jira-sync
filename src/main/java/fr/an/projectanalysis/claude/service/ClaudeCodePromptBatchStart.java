package fr.an.projectanalysis.claude.service;

import fr.an.projectanalysis.service.ChangeLogEvent;
import lombok.Getter;

import java.time.LocalDateTime;

/** A recorded start/success/error of a {@code claude} CLI prompt invocation, keyed by its pid. */
@Getter
public class ClaudeCodePromptBatchStart extends ChangeLogEvent {

    private long runningBatchId;

    private final String prompt;

    /** os process id */
    private final long pid;

    public ClaudeCodePromptBatchStart(LocalDateTime timestamp, long runningBatchId, String prompt, long pid) {
        super(timestamp);
        this.runningBatchId = runningBatchId;
        this.pid = pid;
        this.prompt = prompt;
    }

    @Override
    public String summary() {
        return "Claude prompt start runningBatchId:" + runningBatchId + " (pid " + pid + ") : " + prompt;
    }

}
