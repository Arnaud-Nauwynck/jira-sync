package fr.an.projectanalysis.github.rest;

import fr.an.projectanalysis.github.rest.dtos.GitHubPrPartitionStatsDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrQueryDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.NearbyGitHubPullRequestsDTO;
import fr.an.projectanalysis.github.rest.dtos.UserGitHubPullRequestStatsDTO;
import fr.an.projectanalysis.github.service.GitHubPullRequestService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@RestController
@RequestMapping(path = "/api/v1/github-pull-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "GitHubPullRequests")
@Slf4j
public class GitHubPullRequestsRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/github-pull-requests";

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
        return withLog(log, "POST", "/query", "limit=" + (query != null ? query.limit : null),
                () -> delegate.queryPullRequests(query));
    }

    @Operation(summary = "Same as /query, but returns only the matching PR numbers, without fetching the full pull request objects")
    @PostMapping("/query-ids")
    public Collection<Integer> queryPullRequestIds(@RequestBody GitHubPrQueryDTO query) {
        return withLog(log, "POST", "/query-ids", "limit=" + (query != null ? query.limit : null),
                () -> delegate.queryPullRequestIds(query));
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

}
