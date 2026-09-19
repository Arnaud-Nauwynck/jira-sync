package fr.an.projectanalysis.claude.service;

import fr.an.projectanalysis.service.ChangeLogEvent;
import lombok.Getter;

/** A recorded start/success/error of a {@code claude} CLI prompt invocation, keyed by its pid. */
@Getter
public class ClaudeCodePromptEndChange extends ChangeLogEvent {

    private long time;

    private final int exitCode;
    private final String output;

    // redundant with "sessionId"
    private long startTime;
    private final String prompt;
    private final long pid;

    public ClaudeCodePromptEndChange(
            long time,
            int exitCode, String output,
            long startTime, String prompt, long pid
            ) {
        super();
        this.time = time;
        this.exitCode = exitCode;
        this.output = output;

        this.startTime = startTime;
        this.prompt = prompt;
        this.pid = pid;
    }

    @Override
    public String summary() {
        return "Claude prompt End (pid " + pid + ") took " + ((time - startTime)/1000) + "s, output: " + output;
    }

}
