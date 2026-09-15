package fr.an.projectanalysis.github.rest;

import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.UserGitHubPullRequestStatsDTO;
import fr.an.projectanalysis.github.service.GitHubPullRequestService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
        return withLog("GET", "/pull-requests/" + number, "", () -> {
            GitHubPullRequestDTO found = delegate.findByNumber(number);
            return found != null ? ResponseEntity.ok(found) : ResponseEntity.notFound().build();
        });
    }

    @Operation(summary = "List pull requests created between fromYear and toYear (inclusive), optionally filtered by author login, PR number range, PR number pattern, merged/mergeable tri-state, and/or mergeable-state pattern")
    @GetMapping("/pull-requests")
    public Collection<GitHubPullRequestDTO> queryPullRequests(
            @RequestParam(name = "fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name = "toYear", defaultValue = "2050") int toYear,
            @RequestParam(name = "usernamePattern", required = false) String usernamePattern,
            @RequestParam(name = "fromPullRequestNumber", required = false) Integer fromPullRequestNumber,
            @RequestParam(name = "toPullRequestNumber", required = false) Integer toPullRequestNumber,
            @RequestParam(name = "pullRequestNumberPattern", required = false) String pullRequestNumberPattern,
            @RequestParam(name = "merged", required = false) Boolean merged,
            @RequestParam(name = "mergeable", required = false) Boolean mergeable,
            @RequestParam(name = "mergeableStatePattern", required = false) String mergeableStatePattern
    ) {
        String paramsText = "fromYear=" + fromYear + "&toYear=" + toYear + "&usernamePattern=" + usernamePattern
                + "&fromPullRequestNumber=" + fromPullRequestNumber + "&toPullRequestNumber=" + toPullRequestNumber
                + "&pullRequestNumberPattern=" + pullRequestNumberPattern
                + "&merged=" + merged + "&mergeable=" + mergeable + "&mergeableStatePattern=" + mergeableStatePattern;
        return withLog("GET", "/pull-requests", paramsText, () -> delegate.queryPullRequests(fromYear, toYear, usernamePattern,
                fromPullRequestNumber, toPullRequestNumber, pullRequestNumberPattern, merged, mergeable, mergeableStatePattern));
    }

    @Operation(summary = "Count pull requests created per author, for PRs created between fromYear and toYear (inclusive), optionally filtered by author login")
    @GetMapping("/user-pull-request-stats")
    public Collection<UserGitHubPullRequestStatsDTO> queryUserPullRequestStats(
            @RequestParam(name = "fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name = "toYear", defaultValue = "2050") int toYear,
            @RequestParam(name = "usernamePattern", required = false) String usernamePattern
    ) {
        String paramsText = "fromYear=" + fromYear + "&toYear=" + toYear + "&usernamePattern=" + usernamePattern;
        return withLog("GET", "/user-pull-request-stats", paramsText,
                () -> delegate.queryUserPullRequestStats(fromYear, toYear, usernamePattern));
    }

}
