package fr.an.projectanalysis.claude.service;

import fr.an.projectanalysis.claude.configuration.ClaudeProperties;
import fr.an.projectanalysis.service.RecentChangeLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Invokes the local {@code claude} CLI (Claude Code) with a one-shot prompt, and returns its
 * {@code --output-format json} response text. */
@Component
@Slf4j
public class ClaudeCodePromptInvokerService {

    private final ClaudeProperties props;

    private final RecentChangeLogService changeLogService;

    private final ObjectMapper mapper;

    public ClaudeCodePromptInvokerService(ClaudeProperties props, RecentChangeLogService changeLogService, ObjectMapper mapper) {
        this.props = props;
        this.changeLogService = changeLogService;
        this.mapper = mapper;
    }

    public String invokePrompt(String prompt) throws IOException, InterruptedException {
        return invokePrompt(prompt, List.of());
    }

    /** @param allowedTools MCP/built-in tool names to pre-approve (via {@code --allowedTools}), so the
     * headless CLI does not block on a permission prompt it has no TTY to show; empty means none pre-approved. */
    public String invokePrompt(String prompt, List<String> allowedTools) throws IOException, InterruptedException {
        return invokePrompt(prompt, allowedTools, false);
    }

    /** @param verboseOutput when true, the output logged on completion is pretty-printed as JSON
     * (falling back to the raw text if it does not parse as JSON); when false, it is logged as-is. */
    public String invokePrompt(String prompt, List<String> allowedTools, boolean verboseOutput) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        List<String> command = new ArrayList<>(List.of("claude", "-p", prompt, "--output-format", "json"));
        if (!allowedTools.isEmpty()) {
            command.add("--allowedTools");
            command.add(String.join(",", allowedTools));
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        if (props.getWorkingDir() != null && !props.getWorkingDir().isBlank()) {
            pb.directory(new File(props.getWorkingDir()));
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();
        long pid = process.pid();
        log.info("Launched Claude CLI process pid={}, prompt=\"{}\"", pid, prompt);
        changeLogService.addEvent(new ClaudeCodePromptStartChange(startTime, prompt, pid));

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        long endTime = System.currentTimeMillis();
        if (exitCode != 0) {
            log.error("Claude CLI process pid={} failed with exit code {}, output: \n{}", pid, exitCode, formatOutput(output, verboseOutput));
            changeLogService.addEvent(new ClaudeCodePromptEndChange(endTime, exitCode, output, //
                    startTime, prompt, pid));
            throw new IOException("Claude CLI exited with code " + exitCode + ": " + output);
        }
        log.info("Claude CLI process pid={} succeeded, output: {}", pid, formatOutput(output, verboseOutput));
        changeLogService.addEvent(new ClaudeCodePromptEndChange(endTime, exitCode, output, //
                startTime, prompt, pid));
        return output;
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
