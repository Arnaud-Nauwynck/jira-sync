package fr.an.jira.mcp;

import fr.an.jira.rest.dtos.IssueExtraFieldsDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO;
import fr.an.jira.rest.dtos.UserIssueCreateStatsDTO;
import fr.an.jira.service.JiraIssueService;
import fr.an.jira.service.JiraSyncRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * MCP tools exposing the local Jira issue mirror ({@link JiraIssueService}, {@link JiraSyncRunner}).
 */
@Component
@Slf4j
public class JiraMcpTools {

    private final JiraIssueService issueService;
    private final JiraSyncRunner jiraSyncRunner;

    public JiraMcpTools(JiraIssueService issueService, JiraSyncRunner jiraSyncRunner) {
        this.issueService = issueService;
        this.jiraSyncRunner = jiraSyncRunner;
    }

    @Tool(description = "Find a single locally-synced Jira issue by its key (e.g. 'SPARK-123'). Returns null if not found.")
    public JiraIssueDTO findIssueByKey(
            @ToolParam(description = "Jira issue key, e.g. SPARK-123") String key) {
        log.info("mcp tool findAnnotatedIssueByKey({})", key);
        return issueService.findAnnotatedIssueByKey(key);
    }

    @Tool(description = "List locally-synced Jira issues created between fromYear and toYear (inclusive), optionally filtered by creator/reporter username regex")
    public List<JiraIssueDTO> queryIssues(
            @ToolParam(description = "Earliest creation year, inclusive") int fromYear,
            @ToolParam(description = "Latest creation year, inclusive") int toYear,
            @ToolParam(description = "Optional regex to filter by creator/reporter username", required = false) String usernamePattern) {
        log.info("mcp tool queryAnnotatedIssues(fromYear={}, toYear={}, usernamePattern={})", fromYear, toYear, usernamePattern);
        return issueService.queryAnnotatedIssues(fromYear, toYear, usernamePattern);
    }

    @Tool(description = "Count issues created per user (with a per-year breakdown), for issues created between fromYear and toYear (inclusive), filtered by creator/reporter username regex")
    public Collection<UserIssueCreateStatsDTO> queryUserIssueCreateStats(
            @ToolParam(description = "Earliest creation year, inclusive") int fromYear,
            @ToolParam(description = "Latest creation year, inclusive") int toYear,
            @ToolParam(description = "Regex to filter by creator/reporter username") String usernamePattern) {
        log.info("mcp tool queryUserIssueCreateStats(fromYear={}, toYear={}, usernamePattern={})", fromYear, toYear, usernamePattern);
        return issueService.queryUserIssueCreateStats(fromYear, toYear, usernamePattern);
    }

    @Tool(description = "Run the Jira synchronization for the configured project, pulling new/updated issues from the remote Jira server into the local mirror.")
    public String runJiraSyncAll() throws Exception {
        log.info("mcp tool runJiraSyncAll()");
        long startTime = System.currentTimeMillis();
        jiraSyncRunner.syncAll();
        int millis = (int) (System.currentTimeMillis() - startTime);
        return "sync completed in " + millis + " ms";
    }

    @Tool(description = "Get jira issue annotation data")
    public IssueExtraFieldsDTO getJiraAnnotationFields(
            @ToolParam(description = "key") String key
    ) {
        log.info("mcp tool getJiraAnnotationFields({}, ..)", key);
        JiraIssueDTO issue = issueService.getByKey(key);
        IssueExtraFieldsDTO annotated = issue.getAnnotated();
        return annotated;
    }

    @Tool(description = "Set jira issue annotation analysisSummary = "
            + "human readable summarized text, result of agent analysis on the issue (phase 1, plan only, read-only), "
            + "to explain synthetically the issue, current status, progress and remaining steps"
            + " (compacting all jira issue changelog, histories changes, and correlating with git commits)")
    public void setJiraAnnotationSummarised(
            @ToolParam(description = "key") String key,
            @ToolParam(description = "analysis summary human readable text result") String analysisSummary,
            @ToolParam(description = "analysis summary tokens consumed") int analysisSummaryTokensConsumed
    ) {
        log.info("mcp tool updateJiraAnnotation({}, ..) tokens:{}\n" +
                "analysisSummary:\n{}", key, analysisSummaryTokensConsumed, analysisSummary);
        JiraIssueDTO issue = issueService.getByKey(key);
        IssueExtraFieldsDTO annotated = issue.annotatedOrCreate();
        annotated.analysisSummary = analysisSummary;
        annotated.analysisSummaryLastUpdateTime = LocalDateTime.now();
        annotated.analysisSummaryTokensConsumed = analysisSummaryTokensConsumed;
    }

    @Tool(description = "Set jira issue annotations developmentWorkDescribed = "
            + "result comment text from agent/user development work on the issue (phase 2, development, write local changes)"
            + ": triage, difficulty, what has been done, what remains to be done, etc..")
    public void putJiraAnnotationComment(
            @ToolParam(description = "key") String key,
            @ToolParam(description = "development work described") String developmentWorkDescribed,
            @ToolParam(description = "development work tokens consumed") int developmentWorkTokensConsumed
    ) {
        log.info("mcp tool updateJiraAnnotationComment({}, ..) tokens:{}\n"
                + "developmentWorkDescribed: \n{}", key, developmentWorkTokensConsumed, developmentWorkDescribed);
        JiraIssueDTO issue = issueService.getByKey(key);
        IssueExtraFieldsDTO annotated = issue.annotatedOrCreate();
        annotated.developmentWorkDescribed = developmentWorkDescribed;
        annotated.developmentWorkTokensConsumed = developmentWorkTokensConsumed;
        annotated.developmentWorkLastUpdateTime = LocalDateTime.now();
    }

}
