package fr.an.projectanalysis.jira.rest;

import fr.an.projectanalysis.jira.rest.dtos.IssuesPartitionStatsDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesQueryDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.jira.rest.dtos.UserJiraIssueStatsDTO;
import fr.an.projectanalysis.jira.service.JiraIssueService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping(path="/api/v1/jira-issues", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "JiraIssues")
public class JiraIssuesRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/jira-issues";

    private final JiraIssueService delegate;

    public JiraIssuesRestController(JiraIssueService delegate) {
        super(BASE_URL);
        this.delegate = delegate;
    }

    @Operation(summary = "Count issues created per user, for issues created between fromYear and toYear (inclusive), optionally filtered by summary/description/comment criteria")
    @GetMapping("/user-issue-create-stats")
    public Collection<UserJiraIssueStatsDTO> queryUserIssueCreateStats(
            @RequestParam(name="fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name="toYear", defaultValue = "2050") int toYear,
            @RequestParam(name="usernamePattern") String usernamePattern,
            @RequestParam(name="summaryPattern", required = false) String summaryPattern,
            @RequestParam(name="descriptionPattern", required = false) String descriptionPattern,
            @RequestParam(name="commentPattern", required = false) String commentPattern,
            @RequestParam(name="commentAuthorPattern", required = false) String commentAuthorPattern
    ) {
        String paramsText = "fromYear=" + fromYear + "&toYear=" + toYear + "&usernamePattern=" + usernamePattern
                + "&summaryPattern=" + summaryPattern + "&descriptionPattern=" + descriptionPattern
                + "&commentPattern=" + commentPattern + "&commentAuthorPattern=" + commentAuthorPattern;
        return withLog("GET", "/user-issue-create-stats", paramsText, () -> delegate.queryUserIssueStats(fromYear, toYear, usernamePattern,
                summaryPattern, descriptionPattern, commentPattern, commentAuthorPattern));
    }

    @Operation(summary = "Find a single issue by its key")
    @GetMapping("/by-key/{key}")
    public ResponseEntity<JiraIssueDTO> findIssueByKey(@PathVariable("key") String key) {
        return withLog("GET", "/by-key/" + key, "", () -> {
            JiraIssueDTO found = delegate.findAnnotatedIssueByKey(key);
            return found != null ? ResponseEntity.ok(found) : ResponseEntity.notFound().build();
        });
    }

    @Operation(summary = "List issues matching the given criteria (Data Fetching + Main/Analysis/Development Work/Personal Interest "
            + "filter criteria of the issues-list page), capped at the given limit (default 1000)")
    @PostMapping("/query")
    public Collection<JiraIssueDTO> queryIssues(@RequestBody IssuesQueryDTO query) {
        return withLog("POST", "/query", "limit=" + (query != null ? query.limit : null),
                () -> delegate.queryIssues(query));
    }

    @Operation(summary = "Same as /query, but returns only the matching issue keys, without fetching the full issue objects")
    @PostMapping("/query-ids")
    public Collection<String> queryIssueIds(@RequestBody IssuesQueryDTO query) {
        return withLog("POST", "/query-ids", "limit=" + (query != null ? query.limit : null),
                () -> delegate.queryIssueIds(query));
    }

    @Operation(summary = "Count of locally-synced issues per \"created_year\" partition")
    @GetMapping("/partition-stats")
    public IssuesPartitionStatsDTO queryPartitionStats() {
        return withLog("GET", "/partition-stats", "", delegate::queryPartitionStats);
    }

}
