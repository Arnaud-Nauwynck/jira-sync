package fr.an.projectanalysis.rest;

import fr.an.projectanalysis.rest.dtos.UserActivityStatsDTO;
import fr.an.projectanalysis.service.UserActivityStatsService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/** Exposes {@link UserActivityStatsService} for the Angular UI. */
@RestController
@RequestMapping(path = "/api/v1/user-activity-stats", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "UserActivityStats")
@Slf4j
public class UserActivityStatsRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/user-activity-stats";

    private final UserActivityStatsService delegate;

    public UserActivityStatsRestController(UserActivityStatsService delegate) {
        super(BASE_URL);
        this.delegate = delegate;
    }

    @Operation(summary = "Combined per-user, per-month activity stats: jiraIssue{Created|Updated|Commented|"
            + "ClosedRejected|CloseResolved}, githubPullRequest{Created|Updated|Commented|Merged|Closed}, "
            + "mailMessage{Sent|Replied|Voted}, for events between fromYear and toYear (inclusive)")
    @GetMapping
    public Collection<UserActivityStatsDTO> queryUserActivityStats(
            @RequestParam(name = "fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name = "toYear", defaultValue = "2050") int toYear
    ) {
        String paramsText = "fromYear=" + fromYear + "&toYear=" + toYear;
        return withLog(log, "GET", "", paramsText, () -> delegate.queryUserActivityStats(fromYear, toYear));
    }

}
