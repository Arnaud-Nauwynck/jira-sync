package fr.an.projectanalysis.jira.rest;

import fr.an.projectanalysis.jira.rest.dtos.JiraSyncStatusDTO;
import fr.an.projectanalysis.jira.service.JiraSyncRunner;
import fr.an.projectanalysis.util.AbstractRestController;
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
public class JiraSyncRestController extends AbstractRestController {

     private static final String BASE_URL = "/api/v1/jira-sync";

    private final JiraSyncRunner jiraSyncRunner;

    public JiraSyncRestController(JiraSyncRunner jiraSyncRunner) {
        super(BASE_URL);
        this.jiraSyncRunner = jiraSyncRunner;
    }

    @Operation(summary = "Get info about the last successful Jira sync run")
    @GetMapping("/last-sync")
    public JiraSyncStatusDTO getLastSync() {
        return withLog(log, "GET", "/last-sync", "", () -> {
            JiraSyncStatusDTO dto = new JiraSyncStatusDTO();
            dto.lastSyncTime = jiraSyncRunner.loadLastSyncTime();
            return dto;
        });
    }

    @Operation(summary = "Run the Jira synchronization for all configured projects")
    @PostMapping("/run-sync-all")
    public void runSyncAll() {
        withLog(log, "POST", "/run-sync-all", "", jiraSyncRunner::syncAll);
    }
}
