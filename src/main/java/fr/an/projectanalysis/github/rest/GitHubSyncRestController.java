package fr.an.projectanalysis.github.rest;

import fr.an.projectanalysis.github.client.GitHubApiClient;
import fr.an.projectanalysis.github.rest.dtos.GitHubRateLimitDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubSyncStatusDTO;
import fr.an.projectanalysis.github.service.GitHubPullRequestSyncRunner;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/github-sync", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "GitHubSync")
public class GitHubSyncRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/github-sync";

    private final GitHubPullRequestSyncRunner gitHubPrSyncRunner;

    private final GitHubApiClient gitHubApiClient;

    public GitHubSyncRestController(GitHubPullRequestSyncRunner gitHubPrSyncRunner, GitHubApiClient gitHubApiClient) {
        super(BASE_URL);
        this.gitHubPrSyncRunner = gitHubPrSyncRunner;
        this.gitHubApiClient = gitHubApiClient;
    }

    @Operation(summary = "Get info about the last successful GitHub pull-request sync run")
    @GetMapping("/last-sync")
    public GitHubSyncStatusDTO getLastSync() {
        return withLog("GET", "/last-sync", "", () -> {
            GitHubSyncStatusDTO dto = new GitHubSyncStatusDTO();
            dto.lastSyncTime = gitHubPrSyncRunner.loadLastSyncTime();
            return dto;
        });
    }

    @Operation(summary = "Run the GitHub pull-request synchronization for the configured org/repo")
    @PostMapping("/run-sync-all")
    public void runSyncAll() {
        withLog("POST", "/run-sync-all", "", gitHubPrSyncRunner::syncAll);
    }

    @Operation(summary = "Backfill missing pull-request issue comments for PRs already synced locally")
    @PostMapping("/complete-missing-comments")
    public void completeMissingComments() {
        withLog("POST", "/complete-missing-comments", "", gitHubPrSyncRunner::completeMissingComments);
    }

    @Operation(summary = "Backfill missing pull-request review comments for PRs already synced locally")
    @PostMapping("/complete-missing-review-comments")
    public void completeMissingReviewComments() {
        withLog("POST", "/complete-missing-review-comments", "", gitHubPrSyncRunner::completeMissingReviewComments);
    }

    @Operation(summary = "Backfill missing pull-request events for PRs already synced locally")
    @PostMapping("/complete-missing-events")
    public void completeMissingEvents() {
        withLog("POST", "/complete-missing-events", "", gitHubPrSyncRunner::completeMissingIssueEvents);
    }

    @Operation(summary = "Backfill missing pull-request commits for PRs already synced locally")
    @PostMapping("/complete-missing-commits")
    public void completeMissingCommits() {
        withLog("POST", "/complete-missing-commits", "", gitHubPrSyncRunner::completeMissingIssueCommits);
    }



    @Operation(summary = "Get the current GitHub API rate limit status (proxies GET https://api.github.com/rate_limit)")
    @GetMapping("/rate-limit")
    public GitHubRateLimitDTO getRateLimit() {
        return withLog("GET", "/rate-limit", "", () -> {
            GitHubRateLimitDTO dto = gitHubApiClient.callHttpGet("/rate_limit", GitHubRateLimitDTO.class);
            dto.lastCallLimits = toLastCallLimits(gitHubApiClient.getLastRateLimitStatus());
            return dto;
        });
    }

    @Operation(summary = "Get the rate limit status tracked locally from the headers of the last GitHub API call, "
            + "without making a live call to GitHub (cheap enough to poll regularly)")
    @GetMapping("/last-rate-limit")
    public GitHubRateLimitDTO.LastCallLimits getLastRateLimit() {
        // NO log needed .. return withLog("GET", "/last-rate-limit", "", () -> toLastCallLimits(gitHubApiClient.getLastRateLimitStatus()));
        return toLastCallLimits(gitHubApiClient.getLastRateLimitStatus());
    }

    private static GitHubRateLimitDTO.LastCallLimits toLastCallLimits(GitHubApiClient.RateLimitStatus status) {
        GitHubRateLimitDTO.LastCallLimits lastCallLimits = new GitHubRateLimitDTO.LastCallLimits();
        lastCallLimits.rateLimitRemaining = status.rateLimitRemaining();
        lastCallLimits.rateLimitReset = status.rateLimitReset();
        lastCallLimits.retryAfter = status.retryAfter();
        return lastCallLimits;
    }
}
