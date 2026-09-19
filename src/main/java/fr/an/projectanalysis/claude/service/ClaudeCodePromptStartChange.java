package fr.an.projectanalysis.claude.service;

import fr.an.projectanalysis.service.ChangeLogEvent;
import lombok.Getter;

/** A recorded start/success/error of a {@code claude} CLI prompt invocation, keyed by its pid. */
@Getter
public class ClaudeCodePromptStartChange extends ChangeLogEvent {

    private long time;
    private final String prompt;
    private final long pid;

    public ClaudeCodePromptStartChange(long time, String prompt, long pid) {
        super();
        this.time = time;
        this.pid = pid;
        this.prompt = prompt;
    }

    @Override
    public String summary() {
        return "Claude prompt start (pid " + pid + ") : " + prompt;
    }

}
