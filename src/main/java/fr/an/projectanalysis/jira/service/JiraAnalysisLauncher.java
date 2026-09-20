package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.claude.service.ClaudeCodePromptInvokerService;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptResponseDTO;
import fr.an.projectanalysis.claude.service.ClaudeCodePromptRunningBatch;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/** Launches the claude-code "/jira-analysis" prompt for a single Jira issue. */
@Component
public class JiraAnalysisLauncher {

    /** Tools "/jira-analysis" needs (issue lookup, writing the analysis back to Jira, git-log
     * correlation, and saving its local markdown report), pre-approved so the headless CLI does
     * not block on a permission prompt it has no TTY to show. */
    private static final List<String> CLAUDE_JIRA_ANALYSIS_ALLOWED_TOOLS = List.of(
            "mcp__annotated-jira__findIssueByKey", //
            "mcp__annotated-jira__getJiraAnnotationFields", //
            "mcp__annotated-jira__setJiraAnnotationSummarised", //
            "mcp__annotated-jira__findPullRequestByNumber", //
            "Bash", //
            "Write" // for end result as text files
    );

    private final ClaudeCodePromptInvokerService claudeCodePromptInvokerService;

    public JiraAnalysisLauncher(ClaudeCodePromptInvokerService claudeCodePromptInvokerService) {
        this.claudeCodePromptInvokerService = claudeCodePromptInvokerService;
    }

    public @NonNull ClaudeCodePromptRunningBatch launchClaudeJiraAnalysis(String jiraKey) throws IOException, InterruptedException {
        return claudeCodePromptInvokerService.invokePrompt("/jira-analysis " + jiraKey, CLAUDE_JIRA_ANALYSIS_ALLOWED_TOOLS);
    }

}
