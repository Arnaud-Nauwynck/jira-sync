package fr.an.projectanalysis.jira.rest;

import fr.an.projectanalysis.jira.rest.dtos.JiraSyncStatusDTO;
import fr.an.projectanalysis.jira.service.JiraSyncRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path="/api/v1/jira-sync", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "JiraSync")
@Slf4j
public class JiraSyncRestController {

     private static final String BASE_URL = "/api/v1/jira-sync";

    private final JiraSyncRunner jiraSyncRunner;

    public JiraSyncRestController(JiraSyncRunner jiraSyncRunner) {
        this.jiraSyncRunner = jiraSyncRunner;
    }

    @Operation(summary = "Get info about the last successful Jira sync run")
    @GetMapping("/last-sync")
    public JiraSyncStatusDTO getLastSync() {
        log.info("http GET " + BASE_URL + "/last-sync");
        JiraSyncStatusDTO dto = new JiraSyncStatusDTO();
        dto.lastSyncTime = jiraSyncRunner.loadLastSyncTime();
        return dto;
    }

    @Operation(summary = "Run the Jira synchronization for all configured projects")
    @PostMapping("/run-sync-all")
    public void runSyncAll() {
        log.info("http POST " + BASE_URL + "/run-sync-all");
        long startTime = System.currentTimeMillis();
        try {
            jiraSyncRunner.syncAll();

            int millis = (int) (System.currentTimeMillis() - startTime);
            log.info("... done http POST /run-sync-all, took {} ms", millis);
        } catch (Exception ex) {
            int millis = (int) (System.currentTimeMillis() - startTime);
            log.error("... Failed http POST /run-sync-all, took {} ms, rethrowing {}", millis, ex.getMessage());
            throw new RuntimeException(ex);
        }
    }
}
