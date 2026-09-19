package fr.an.projectanalysis.jira.rest;

import fr.an.projectanalysis.jira.rest.dtos.IssueIdAndLastUpdateTimeDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesCompareIdsResultDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesCompareQueryDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesPartitionStatsDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesQueryDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.jira.rest.dtos.NearbyJiraIssuesDTO;
import fr.an.projectanalysis.jira.rest.dtos.UserJiraIssueStatsDTO;
import fr.an.projectanalysis.jira.service.JiraIssueCriteria;
import fr.an.projectanalysis.jira.service.JiraIssueService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;

@RestController
@RequestMapping(path="/api/v1/jira-issues", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "JiraIssues")
@Slf4j
public class JiraIssuesRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/jira-issues";

    /** Caps the number of results of /query and /query-ids, when the request does not set its own limit. */
    private static final int DEFAULT_LIMIT = 1000;

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
        JiraIssueCriteria issueCriteria = JiraIssueCriteria.ofUserStatsPatterns(fromYear, toYear, usernamePattern,
                summaryPattern, descriptionPattern, commentPattern, commentAuthorPattern);
        return withLog(log, "GET", "/user-issue-create-stats", paramsText,
                () -> delegate.queryUserIssueStats(issueCriteria));
    }

    @Operation(summary = "Find a single issue by its key")
    @GetMapping("/by-key/{key}")
    public ResponseEntity<JiraIssueDTO> findIssueByKey(@PathVariable("key") String key) {
        return withLog(log, "GET", "/by-key/" + key, "", () -> {
            JiraIssueDTO found = delegate.findAnnotatedIssueByKey(key);
            return found != null ? ResponseEntity.ok(found) : ResponseEntity.notFound().build();
        });
    }

    @Operation(summary = "Find the issues having the given keys (\"ids\"), returned in the request order; "
            + "keys not found locally are silently skipped")
    @PostMapping("/by-ids")
    public Collection<JiraIssueDTO> findIssuesByIds(@RequestBody @Nonnull List<String> ids) {
        return withLog(log, "POST", "/by-ids", "ids.size=" + ids.size(),
                () -> delegate.findIssuesByIds(ids));
    }

    @Operation(summary = "For the issue with the given key, finds the keys of the nearest earlier (\"prev\") and later "
            + "(\"next\") issue, within the same Jira project, that is still open, still open and created by the same "
            + "author, or created by the same author (regardless of status)")
    @GetMapping("/by-key/{key}/nearby")
    public NearbyJiraIssuesDTO findNearbyIssues(@PathVariable("key") String key) {
        return withLog(log, "GET", "/by-key/" + key + "/nearby", "", () -> delegate.findNearbyIssues(key));
    }

    @Operation(summary = "List issues matching the given criteria (Data Fetching + Main/Analysis/Development Work/Personal Interest "
            + "filter criteria of the issues-list page), capped at the given limit (default 1000)")
    @PostMapping("/query")
    public Collection<JiraIssueDTO> queryIssues(@RequestBody @Nonnull IssuesQueryDTO query) {
        JiraIssueCriteria issueCriteria = criteriaOf(query);
        int limit = limitOf(query);
        return withLog(log, "POST", "/query", "limit=" + limit,
                () -> delegate.queryIssues(issueCriteria, limit));
    }

    @Operation(summary = "Same as /query, but returns only the matching issue keys, without fetching the full issue objects")
    @PostMapping("/query-ids")
    public Collection<String> queryIssueIds(@RequestBody IssuesQueryDTO query) {
        JiraIssueCriteria issueCriteria = criteriaOf(query);
        int limit = limitOf(query);
        return withLog(log, "POST", "/query-ids", "limit=" + limit,
                () -> delegate.queryIssueIds(issueCriteria, limit));
    }

    @Operation(summary = "Same as /query-ids, but returns for each matching issue its key (\"id\") with its last "
            + "update time (\"t\", in epoch milliseconds)")
    @PostMapping("/query-id-and-last-update-times")
    public Collection<IssueIdAndLastUpdateTimeDTO> queryIssueIdAndLastUpdateTimes(@RequestBody IssuesQueryDTO query) {
        JiraIssueCriteria issueCriteria = criteriaOf(query);
        int limit = limitOf(query);
        return withLog(log, "POST", "/query-id-and-last-update-times", "limit=" + limit,
                () -> delegate.queryIssueIdAndLastUpdateTimes(issueCriteria, limit));
    }

    @Operation(summary = "Compares the issue keys matched by 2 independent criteria: keys matched by the left criteria "
            + "only, by both (listed only when fillCommonIds is true, else only counted), and by the right criteria "
            + "only; each side capped at the given limit (default 1000)")
    @PostMapping("/compare-query-ids")
    public IssuesCompareIdsResultDTO compareQueryIds(@RequestBody @Nonnull IssuesCompareQueryDTO query) {
        JiraIssueCriteria leftCriteria = new JiraIssueCriteria(query.leftCriteria);
        JiraIssueCriteria rightCriteria = new JiraIssueCriteria(query.rightCriteria);
        boolean fillCommonIds = query.fillCommonIds;
        int leftLimit = (query.leftLimit != null) ? query.leftLimit : DEFAULT_LIMIT;
        int rightLimit = (query.rightLimit != null) ? query.rightLimit : DEFAULT_LIMIT;
        return withLog(log, "POST", "/compare-query-ids", "fillCommonIds=" + fillCommonIds
                        + "&leftLimit=" + leftLimit + "&rightLimit=" + rightLimit,
                () -> delegate.compareQueryIds(leftCriteria, leftLimit, rightCriteria, rightLimit, fillCommonIds),
                res -> res.leftOnlyIds.size() + " left-only, " + res.commonCount + " common, "
                        + res.rightOnlyIds.size() + " right-only");
    }

    @Operation(summary = "Count of locally-synced issues per \"created_year\" partition")
    @GetMapping("/partition-stats")
    public IssuesPartitionStatsDTO queryPartitionStats() {
        return withLog(log, "GET", "/partition-stats", "", delegate::queryPartitionStats);
    }

    /** Resolves the request body criteria DTO into the criteria object used by the service. */
    private static JiraIssueCriteria criteriaOf(IssuesQueryDTO query) {
        return new JiraIssueCriteria(query != null ? query.criteria : null);
    }

    private static int limitOf(IssuesQueryDTO query) {
        return (query != null && query.limit != null) ? query.limit : DEFAULT_LIMIT;
    }

}
