package fr.an.projectanalysis.github.rest;

import fr.an.projectanalysis.github.rest.dtos.GitHubPrCompareIdsResultDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrCompareQueryDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrIdAndLastUpdateTimeDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrPartitionStatsDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrQueryDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.NearbyGitHubPullRequestsDTO;
import fr.an.projectanalysis.github.rest.dtos.UserGitHubPullRequestStatsDTO;
import fr.an.projectanalysis.github.service.GitHubPrCriteria;
import fr.an.projectanalysis.github.service.GitHubPullRequestService;
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
@RequestMapping(path = "/api/v1/github-pull-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "GitHubPullRequests")
@Slf4j
public class GitHubPullRequestsRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/github-pull-requests";

    /** Caps the number of results of /query and /query-ids, when the request does not set its own limit. */
    private static final int DEFAULT_LIMIT = 1000;

    private final GitHubPullRequestService delegate;

    public GitHubPullRequestsRestController(GitHubPullRequestService delegate) {
        super(BASE_URL);
        this.delegate = delegate;
    }

    @Operation(summary = "Find a single pull request by its number")
    @GetMapping("/pull-requests/{number}")
    public ResponseEntity<GitHubPullRequestDTO> findPullRequestByNumber(@PathVariable("number") int number) {
        return withLog(log, "GET", "/pull-requests/" + number, "", () -> {
            GitHubPullRequestDTO found = delegate.findByNumber(number);
            return found != null ? ResponseEntity.ok(found) : ResponseEntity.notFound().build();
        });
    }

    @Operation(summary = "Find the pull requests having the given numbers (\"ids\"), returned in the request order; "
            + "numbers not found locally are silently skipped")
    @PostMapping("/by-ids")
    public Collection<GitHubPullRequestDTO> findByIds(@RequestBody @Nonnull List<Integer> ids) {
        return withLog(log, "POST", "/by-ids", "ids.size=" + ids.size(),
                () -> delegate.findByIds(ids));
    }

    @Operation(summary = "For the PR with the given number, finds the numbers of the nearest earlier (\"prev\") and later "
            + "(\"next\") PR that is still open, still open and created by the same author, or created by the same "
            + "author (regardless of state)")
    @GetMapping("/pull-requests/{number}/nearby")
    public NearbyGitHubPullRequestsDTO findNearbyPullRequests(@PathVariable("number") int number) {
        return withLog(log, "GET", "/pull-requests/" + number + "/nearby", "", () -> delegate.findNearbyPullRequests(number));
    }

    @Operation(summary = "List pull requests matching the given criteria (Data Fetching + Main/Analysis/Development Work/Personal Interest "
            + "filter criteria of the github-pull-requests page), capped at the given limit (default 1000)")
    @PostMapping("/query")
    public Collection<GitHubPullRequestDTO> queryPullRequests(@RequestBody GitHubPrQueryDTO query) {
        GitHubPrCriteria criteria = criteriaOf(query);
        int limit = limitOf(query);
        return withLog(log, "POST", "/query", "limit=" + limit,
                () -> delegate.queryPullRequests(criteria, limit));
    }

    @Operation(summary = "Same as /query, but returns only the matching PR numbers, without fetching the full pull request objects")
    @PostMapping("/query-ids")
    public Collection<Integer> queryPullRequestIds(@RequestBody GitHubPrQueryDTO query) {
        GitHubPrCriteria criteria = criteriaOf(query);
        int limit = limitOf(query);
        return withLog(log, "POST", "/query-ids", "limit=" + limit,
                () -> delegate.queryPullRequestIds(criteria, limit));
    }

    @Operation(summary = "Same as /query-ids, but returns for each matching pull request its number (\"id\") with "
            + "its last update time (\"t\", in epoch milliseconds)")
    @PostMapping("/query-id-and-last-update-times")
    public Collection<GitHubPrIdAndLastUpdateTimeDTO> queryPullRequestIdAndLastUpdateTimes(@RequestBody GitHubPrQueryDTO query) {
        GitHubPrCriteria criteria = criteriaOf(query);
        int limit = limitOf(query);
        return withLog(log, "POST", "/query-id-and-last-update-times", "limit=" + limit,
                () -> delegate.queryPullRequestIdAndLastUpdateTimes(criteria, limit));
    }

    @Operation(summary = "Compares the PR numbers matched by 2 independent criteria: numbers matched by the left "
            + "criteria only, by both (listed only when fillCommonIds is true, else only counted), and by the right "
            + "criteria only; each side capped at its own limit (default 1000)")
    @PostMapping("/compare-query-ids")
    public GitHubPrCompareIdsResultDTO compareQueryIds(@RequestBody @Nonnull GitHubPrCompareQueryDTO query) {
        GitHubPrCriteria leftCriteria = new GitHubPrCriteria(query.leftCriteria);
        GitHubPrCriteria rightCriteria = new GitHubPrCriteria(query.rightCriteria);
        boolean fillCommonIds = query.fillCommonIds;
        int leftLimit = (query.leftLimit != null) ? query.leftLimit : DEFAULT_LIMIT;
        int rightLimit = (query.rightLimit != null) ? query.rightLimit : DEFAULT_LIMIT;
        return withLog(log, "POST", "/compare-query-ids", "fillCommonIds=" + fillCommonIds
                        + "&leftLimit=" + leftLimit + "&rightLimit=" + rightLimit,
                () -> delegate.compareQueryIds(leftCriteria, leftLimit, rightCriteria, rightLimit, fillCommonIds),
                res -> res.leftOnlyIds.size() + " left-only, " + res.commonCount + " common, "
                        + res.rightOnlyIds.size() + " right-only");
    }

    @Operation(summary = "Count of locally-synced pull requests per \"created_year\" partition")
    @GetMapping("/partition-stats")
    public GitHubPrPartitionStatsDTO queryPartitionStats() {
        return withLog(log, "GET", "/partition-stats", "", delegate::queryPartitionStats);
    }

    @Operation(summary = "Count pull requests created per author, for PRs created between fromYear and toYear (inclusive), optionally filtered by author login")
    @GetMapping("/user-pull-request-stats")
    public Collection<UserGitHubPullRequestStatsDTO> queryUserPullRequestStats(
            @RequestParam(name = "fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name = "toYear", defaultValue = "2050") int toYear,
            @RequestParam(name = "usernamePattern", required = false) String usernamePattern
    ) {
        String paramsText = "fromYear=" + fromYear + "&toYear=" + toYear + "&usernamePattern=" + usernamePattern;
        return withLog(log, "GET", "/user-pull-request-stats", paramsText,
                () -> delegate.queryUserPullRequestStats(fromYear, toYear, usernamePattern));
    }

    /** Resolves the request body criteria DTO into the criteria object used by the service. */
    private static GitHubPrCriteria criteriaOf(GitHubPrQueryDTO query) {
        return new GitHubPrCriteria(query != null ? query.criteria : null);
    }

    private static int limitOf(GitHubPrQueryDTO query) {
        return (query != null && query.limit != null) ? query.limit : DEFAULT_LIMIT;
    }

}
