package fr.an.projectanalysis.rest;

import fr.an.projectanalysis.service.ChangeLogEvent;
import fr.an.projectanalysis.service.RecentChangeLogService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Exposes the in-memory {@link RecentChangeLogService} feed for polling by the Angular UI. */
@RestController
@RequestMapping(path = "/api/v1/changelog", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "ChangeLog")
@Slf4j
public class ChangeLogRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/changelog";

    private final RecentChangeLogService recentChangeLogService;

    public ChangeLogRestController(RecentChangeLogService recentChangeLogService) {
        super(BASE_URL);
        this.recentChangeLogService = recentChangeLogService;
    }

    @Operation(summary = "List the currently held recent change events (Jira issue / GitHub PR / mailing-list), most recent first")
    @GetMapping("/recent")
    public List<ChangeLogEvent> getRecentEvents() {
        return withLog(log, "GET", "/recent", "", recentChangeLogService::getRecentEvents);
    }

    @Operation(summary = "Poll for change events recorded after the given sequence number (0 on the first call, "
            + "then the highest 'seq' seen so far), oldest first")
    @GetMapping("/since/{sinceSeq}")
    public List<ChangeLogEvent> getEventsSince(@PathVariable long sinceSeq) {
        return withLogDebug(log, "GET", "/since/" + sinceSeq, "", () -> recentChangeLogService.getEventsSince(sinceSeq));
    }

}
