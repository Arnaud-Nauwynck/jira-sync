package fr.an.projectanalysis.github.rest;

import fr.an.projectanalysis.github.rest.dtos.GitHubSyncStatusDTO;
import fr.an.projectanalysis.github.service.GitHubPullRequestSyncRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/github-sync", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "GitHubSync")
@Slf4j
public class GitHubSyncRestController {

    private static final String BASE_URL = "/api/v1/github-sync";

    private final GitHubPullRequestSyncRunner gitHubPrSyncRunner;

    public GitHubSyncRestController(GitHubPullRequestSyncRunner gitHubPrSyncRunner) {
        this.gitHubPrSyncRunner = gitHubPrSyncRunner;
    }

    @Operation(summary = "Get info about the last successful GitHub pull-request sync run")
    @GetMapping("/last-sync")
    public GitHubSyncStatusDTO getLastSync() {
        log.info("http GET " + BASE_URL + "/last-sync");
        GitHubSyncStatusDTO dto = new GitHubSyncStatusDTO();
        dto.lastSyncTime = gitHubPrSyncRunner.loadLastSyncTime();
        return dto;
    }

    @Operation(summary = "Run the GitHub pull-request synchronization for the configured org/repo")
    @PostMapping("/run-sync-all")
    public void runSyncAll() {
        log.info("http POST " + BASE_URL + "/run-sync-all");
        long startTime = System.currentTimeMillis();
        try {
            gitHubPrSyncRunner.syncAll();

            int millis = (int) (System.currentTimeMillis() - startTime);
            log.info("... done http POST /run-sync-all, took {} ms", millis);
        } catch (Exception ex) {
            int millis = (int) (System.currentTimeMillis() - startTime);
            log.error("... Failed http POST /run-sync-all, took {} ms, rethrowing {}", millis, ex.getMessage());
            throw new RuntimeException(ex);
        }
    }
}
