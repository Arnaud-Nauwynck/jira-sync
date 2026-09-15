package fr.an.projectanalysis.github.rest;

import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.UserGitHubPullRequestStatsDTO;
import fr.an.projectanalysis.github.service.GitHubPullRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping(path = "/api/v1/github-pull-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "GitHubPullRequests")
@Slf4j
public class GitHubPullRequestsRestController {

    private static final String BASE_URL = "/api/v1/github-pull-requests";

    private final GitHubPullRequestService delegate;

    public GitHubPullRequestsRestController(GitHubPullRequestService delegate) {
        this.delegate = delegate;
    }

    @Operation(summary = "Find a single pull request by its number")
    @GetMapping("/pull-requests/{number}")
    public ResponseEntity<GitHubPullRequestDTO> findPullRequestByNumber(@PathVariable("number") int number) {
        log.info("http GET {}/pull-requests/{}", BASE_URL, number);
        GitHubPullRequestDTO found = delegate.findByNumber(number);
        return found != null ? ResponseEntity.ok(found) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "List pull requests created between fromYear and toYear (inclusive), optionally filtered by author login, PR number range, and/or PR number pattern")
    @GetMapping("/pull-requests")
    public Collection<GitHubPullRequestDTO> queryPullRequests(
            @RequestParam(name = "fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name = "toYear", defaultValue = "2050") int toYear,
            @RequestParam(name = "usernamePattern", required = false) String usernamePattern,
            @RequestParam(name = "fromPullRequestNumber", required = false) Integer fromPullRequestNumber,
            @RequestParam(name = "toPullRequestNumber", required = false) Integer toPullRequestNumber,
            @RequestParam(name = "pullRequestNumberPattern", required = false) String pullRequestNumberPattern
    ) {
        log.info("http GET {}/pull-requests?fromYear={}&toYear={}&usernamePattern={}&fromPullRequestNumber={}&toPullRequestNumber={}&pullRequestNumberPattern={}",
                BASE_URL, fromYear, toYear, usernamePattern, fromPullRequestNumber, toPullRequestNumber, pullRequestNumberPattern);
        return delegate.queryPullRequests(fromYear, toYear, usernamePattern,
                fromPullRequestNumber, toPullRequestNumber, pullRequestNumberPattern);
    }

    @Operation(summary = "Count pull requests created per author, for PRs created between fromYear and toYear (inclusive), optionally filtered by author login")
    @GetMapping("/user-pull-request-stats")
    public Collection<UserGitHubPullRequestStatsDTO> queryUserPullRequestStats(
            @RequestParam(name = "fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name = "toYear", defaultValue = "2050") int toYear,
            @RequestParam(name = "usernamePattern", required = false) String usernamePattern
    ) {
        log.info("http GET {}/user-pull-request-stats?fromYear={}&toYear={}&usernamePattern={}",
                BASE_URL, fromYear, toYear, usernamePattern);
        return delegate.queryUserPullRequestStats(fromYear, toYear, usernamePattern);
    }

}
