package fr.an.projectanalysis.github.rest;

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

    public GitHubSyncRestController(GitHubPullRequestSyncRunner gitHubPrSyncRunner) {
        super(BASE_URL);
        this.gitHubPrSyncRunner = gitHubPrSyncRunner;
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
}
