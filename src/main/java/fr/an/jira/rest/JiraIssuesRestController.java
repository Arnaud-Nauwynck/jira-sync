package fr.an.jira.rest;

import fr.an.jira.rest.dtos.UserIssueCreateStatsDTO;
import fr.an.jira.service.JiraIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1/jira-issues")
@Tag(name = "JiraIssues")
@Slf4j
public class JiraIssuesRestController {

    private final JiraIssueService delegate;

    public JiraIssuesRestController(JiraIssueService delegate) {
        this.delegate = delegate;
    }

    @Operation(summary = "Count issues created per user, for issues created between fromYear and toYear (inclusive)")
    @GetMapping("/user-issue-create-stats")
    public Collection<UserIssueCreateStatsDTO> queryUserIssueCreateStats(
            @RequestParam(name="fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name="toYear", defaultValue = "2050") int toYear,
            @RequestParam(name="usernamePattern") String usernamePattern
    ) {
        log.info("http GET /user-issue-create-stats?fromYear={}&toYear={}&usernamePattern={}", fromYear, toYear, usernamePattern);
        return delegate.queryUserIssueCreateStats(fromYear, toYear, usernamePattern);
    }
}
