package fr.an.projectanalysis.mailinglist.rest;

import fr.an.projectanalysis.mailinglist.rest.dtos.MailingListSyncStatusDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.UserMailMessageStatsDTO;
import fr.an.projectanalysis.mailinglist.service.MailMessageService;
import fr.an.projectanalysis.mailinglist.service.MailingListSyncRunner;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping(path = "/api/v1/mailing-list-sync", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "MailingListSync")
@Slf4j
public class MailingListSyncRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/mailing-list-sync";

    private final MailingListSyncRunner mailingListSyncRunner;
    private final MailMessageService mailMessageService;

    public MailingListSyncRestController(MailingListSyncRunner mailingListSyncRunner, MailMessageService mailMessageService) {
        super(BASE_URL);
        this.mailingListSyncRunner = mailingListSyncRunner;
        this.mailMessageService = mailMessageService;
    }

    @Operation(summary = "Get info about the last successful mailing-list sync run")
    @GetMapping("/last-sync")
    public MailingListSyncStatusDTO getLastSync() {
        return withLog(log, "GET", "/last-sync", "", () -> {
            MailingListSyncStatusDTO dto = new MailingListSyncStatusDTO();
            dto.lastClosedMonth = mailingListSyncRunner.loadLastClosedMonth();
            return dto;
        });
    }

    @Operation(summary = "Count mailing-list messages per sender, for messages archived between fromYear and toYear (inclusive), optionally filtered by a regex matched against the From header")
    @GetMapping("/user-mail-message-stats")
    public Collection<UserMailMessageStatsDTO> queryUserMailMessageStats(
            @RequestParam(name = "fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name = "toYear", defaultValue = "2050") int toYear,
            @RequestParam(name = "fromPattern", required = false) String fromPattern
    ) {
        String paramsText = "fromYear=" + fromYear + "&toYear=" + toYear + "&fromPattern=" + fromPattern;
        return withLog(log, "GET", "/user-mail-message-stats", paramsText,
                () -> mailMessageService.queryUserMessageStats(fromYear, toYear, fromPattern));
    }

    @Operation(summary = "Run the mailing-list synchronization for the configured list/domain")
    @PostMapping("/run-sync-all")
    public void runSyncAll() {
        withLog(log, "POST", "/run-sync-all", "", mailingListSyncRunner::syncAll);
    }
}
