package fr.an.projectanalysis.jira.mcp;

import fr.an.projectanalysis.jira.repository.JiraIssueRepository;
import fr.an.projectanalysis.jira.rest.dtos.IssueExtraFieldsDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.jira.rest.dtos.UserJiraIssueStatsDTO;
import fr.an.projectanalysis.jira.service.JiraIssueService;
import fr.an.projectanalysis.jira.service.JiraSyncRunner;
import fr.an.projectanalysis.service.McpToolCallChange;
import fr.an.projectanalysis.service.RecentChangeLogService;
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
    private final JiraIssueRepository jiraIssueRepository;
    private final JiraSyncRunner jiraSyncRunner;
    private final RecentChangeLogService changeLogService;

    public JiraMcpTools(JiraIssueService issueService,
                        JiraIssueRepository jiraIssueRepository,
                        JiraSyncRunner jiraSyncRunner,
                        RecentChangeLogService changeLogService
    ) {
        this.issueService = issueService;
        this.jiraIssueRepository = jiraIssueRepository;
        this.jiraSyncRunner = jiraSyncRunner;
        this.changeLogService = changeLogService;
    }

    @Tool(description = "Find a single locally-synced Jira issue by its key (e.g. 'SPARK-123'). Returns null if not found.")
    public JiraIssueDTO findIssueByKey(
            @ToolParam(description = "Jira issue key, e.g. SPARK-123") String key) {
        log.info("mcp tool findAnnotatedIssueByKey({})", key);
        changeLogService.addEvent(new McpToolCallChange("findIssueByKey", "key=" + key));
        return issueService.findAnnotatedIssueByKey(key);
    }

    @Tool(description = "List locally-synced Jira issues created between fromYear and toYear (inclusive), optionally filtered by creator/reporter username regex")
    public List<JiraIssueDTO> queryIssues(
            @ToolParam(description = "Earliest creation year, inclusive") int fromYear,
            @ToolParam(description = "Latest creation year, inclusive") int toYear,
            @ToolParam(description = "Optional regex to filter by creator/reporter username", required = false) String usernamePattern) {
        log.info("mcp tool queryAnnotatedIssues(fromYear={}, toYear={}, usernamePattern={})", fromYear, toYear, usernamePattern);
        changeLogService.addEvent(new McpToolCallChange("queryIssues",
                "fromYear=" + fromYear + "&toYear=" + toYear + "&usernamePattern=" + usernamePattern));
        return issueService.queryAnnotatedIssues(fromYear, toYear, usernamePattern);
    }

    @Tool(description = "Count issues created per user (with a per-year breakdown), for issues created between fromYear and toYear (inclusive), filtered by creator/reporter username regex and optional summary/description/comment criteria")
    public Collection<UserJiraIssueStatsDTO> queryUserIssueCreateStats(
            @ToolParam(description = "Earliest creation year, inclusive") int fromYear,
            @ToolParam(description = "Latest creation year, inclusive") int toYear,
            @ToolParam(description = "Regex to filter by creator/reporter username") String usernamePattern,
            @ToolParam(description = "Optional regex that must be found in the issue summary", required = false) String summaryPattern,
            @ToolParam(description = "Optional regex that must be found in the issue description", required = false) String descriptionPattern,
            @ToolParam(description = "Optional regex that must be found in at least one issue comment's body", required = false) String commentPattern,
            @ToolParam(description = "Optional regex that the author of at least one issue comment must match", required = false) String commentAuthorPattern) {
        log.info("mcp tool queryUserIssueCreateStats(fromYear={}, toYear={}, usernamePattern={}, summaryPattern={}, descriptionPattern={}, commentPattern={}, commentAuthorPattern={})",
                fromYear, toYear, usernamePattern, summaryPattern, descriptionPattern, commentPattern, commentAuthorPattern);
        changeLogService.addEvent(new McpToolCallChange("queryUserIssueCreateStats",
                "fromYear=" + fromYear + "&toYear=" + toYear + "&usernamePattern=" + usernamePattern));
        return issueService.queryUserIssueStats(fromYear, toYear, usernamePattern,
                summaryPattern, descriptionPattern, commentPattern, commentAuthorPattern);
    }

//    @Tool(description = "Run the Jira synchronization for the configured project, pulling new/updated issues from the remote Jira server into the local mirror.")
//    public String runJiraSyncAll() throws Exception {
//        log.info("mcp tool runJiraSyncAll()");
//        long startTime = System.currentTimeMillis();
//        jiraSyncRunner.syncAll();
//        int millis = (int) (System.currentTimeMillis() - startTime);
//        return "sync completed in " + millis + " ms";
//    }

    @Tool(description = "Get jira issue annotation data")
    public IssueExtraFieldsDTO getJiraAnnotationFields(
            @ToolParam(description = "key") String key
    ) {
        log.info("mcp tool getJiraAnnotationFields({}, ..)", key);
        changeLogService.addEvent(new McpToolCallChange("getJiraAnnotationFields", "key=" + key));
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
        changeLogService.addEvent(new McpToolCallChange("setJiraAnnotationSummarised", "key=" + key));
        JiraIssueDTO issue = issueService.getByKey(key);
        IssueExtraFieldsDTO annotated = issue.annotatedOrCreate();
        annotated.analysisSummary = analysisSummary;
        annotated.analysisSummaryLastUpdateTime = LocalDateTime.now();
        annotated.analysisSummaryTokensConsumed = analysisSummaryTokensConsumed;
        jiraIssueRepository.putAnnotation(key, annotated);
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
        changeLogService.addEvent(new McpToolCallChange("putJiraAnnotationComment", "key=" + key));
        JiraIssueDTO issue = issueService.getByKey(key);
        IssueExtraFieldsDTO annotated = issue.annotatedOrCreate();
        annotated.developmentWorkDescribed = developmentWorkDescribed;
        annotated.developmentWorkTokensConsumed = developmentWorkTokensConsumed;
        annotated.developmentWorkLastUpdateTime = LocalDateTime.now();
        jiraIssueRepository.putAnnotation(key, annotated);
    }

}
