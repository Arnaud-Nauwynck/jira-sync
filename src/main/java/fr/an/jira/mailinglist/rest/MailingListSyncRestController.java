package fr.an.jira.mailinglist.rest;

import fr.an.jira.mailinglist.rest.dtos.MailingListSyncStatusDTO;
import fr.an.jira.mailinglist.service.MailingListSyncRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/mailing-list-sync", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "MailingListSync")
@Slf4j
public class MailingListSyncRestController {

    private static final String BASE_URL = "/api/v1/mailing-list-sync";

    private final MailingListSyncRunner mailingListSyncRunner;

    public MailingListSyncRestController(MailingListSyncRunner mailingListSyncRunner) {
        this.mailingListSyncRunner = mailingListSyncRunner;
    }

    @Operation(summary = "Get info about the last successful mailing-list sync run")
    @GetMapping("/last-sync")
    public MailingListSyncStatusDTO getLastSync() {
        log.info("http GET " + BASE_URL + "/last-sync");
        MailingListSyncStatusDTO dto = new MailingListSyncStatusDTO();
        dto.lastClosedMonth = mailingListSyncRunner.loadLastClosedMonth();
        return dto;
    }

    @Operation(summary = "Run the mailing-list synchronization for the configured list/domain")
    @PostMapping("/run-sync-all")
    public void runSyncAll() {
        log.info("http POST " + BASE_URL + "/run-sync-all");
        long startTime = System.currentTimeMillis();
        try {
            mailingListSyncRunner.syncAll();

            int millis = (int) (System.currentTimeMillis() - startTime);
            log.info("... done http POST /run-sync-all, took {} ms", millis);
        } catch (Exception ex) {
            int millis = (int) (System.currentTimeMillis() - startTime);
            log.error("... Failed http POST /run-sync-all, took {} ms, rethrowing {}", millis, ex.getMessage());
            throw new RuntimeException(ex);
        }
    }
}
