package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.claude.service.ClaudeCodePromptInvokerService;
import fr.an.projectanalysis.claude.service.ClaudeCodePromptRunningBatch;
import fr.an.projectanalysis.jira.repository.JiraIssueRepository;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssueExtraFieldsDTO;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
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

    private static final String SKIP_ALREADY_CLOSED_SUMMARY = "skip - already closed";

    private final ClaudeCodePromptInvokerService claudeCodePromptInvokerService;
    private final JiraIssueRepository jiraIssueRepository;
    private final JiraIssueClosedChecker jiraIssueClosedChecker;

    public JiraAnalysisLauncher(ClaudeCodePromptInvokerService claudeCodePromptInvokerService,
                                JiraIssueRepository jiraIssueRepository,
                                JiraIssueClosedChecker jiraIssueClosedChecker) {
        this.claudeCodePromptInvokerService = claudeCodePromptInvokerService;
        this.jiraIssueRepository = jiraIssueRepository;
        this.jiraIssueClosedChecker = jiraIssueClosedChecker;
    }

    public @NonNull ClaudeCodePromptRunningBatch launchClaudeJiraAnalysisOrSkip(String jiraKey) throws IOException {
        JiraIssueDTO issue = jiraIssueRepository.findByKey(jiraKey);
        return launchClaudeJiraAnalysisOrSkip(issue);
    }

    public @NonNull ClaudeCodePromptRunningBatch launchClaudeJiraAnalysisOrSkip(JiraIssueDTO issue) throws IOException {
        if (jiraIssueClosedChecker.isClosed(issue)) {
            return skipAlreadyClosed(issue);
        }
        return claudeCodePromptInvokerService.invokePrompt("/jira-analysis " + issue.key, CLAUDE_JIRA_ANALYSIS_ALLOWED_TOOLS);
    }

    /** saves the analysis result 'skip - already closed', and returns an already-completed batch (no claude process) */
    private ClaudeCodePromptRunningBatch skipAlreadyClosed(JiraIssueDTO issue) {
        IssueExtraFieldsDTO annotated = issue.annotatedOrCreate();
        annotated.analysisSummary = SKIP_ALREADY_CLOSED_SUMMARY;
        annotated.analysisSummaryLastUpdateTime = LocalDateTime.now();
        annotated.analysisSummaryTokensConsumed = 0;
        jiraIssueRepository.putAnnotation(issue.key, annotated);

        ClaudeCodePromptRunningBatch res = new ClaudeCodePromptRunningBatch(-1L,
                "/jira-analysis " + issue.key, List.of(), System.currentTimeMillis(), -1L);
        res.getCompletion().complete(null);
        return res;
    }

}
