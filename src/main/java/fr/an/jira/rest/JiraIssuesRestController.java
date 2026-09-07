package fr.an.jira.rest;

import fr.an.jira.rest.dtos.JiraIssueDTO;
import fr.an.jira.rest.dtos.UserIssueCreateStatsDTO;
import fr.an.jira.service.JiraIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping(path="/api/v1/jira-issues", produces = MediaType.APPLICATION_JSON_VALUE)
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

    @Operation(summary = "Find a single issue by its key")
    @GetMapping("/annotated-issues/{key}")
    public ResponseEntity<JiraIssueDTO> findAnnotatedIssueByKey(@PathVariable("key") String key) {
        log.info("http GET /annotated-issues/{}", key);
        JiraIssueDTO found = delegate.findAnnotatedIssueByKey(key);
        return found != null ? ResponseEntity.ok(found) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "List issues created between fromYear and toYear (inclusive), optionally filtered by creator username")
    @GetMapping("/annotated-issues")
    public Collection<JiraIssueDTO> queryAnnotatedIssues(
            @RequestParam(name="fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name="toYear", defaultValue = "2050") int toYear,
            @RequestParam(name="usernamePattern", required = false) String usernamePattern
    ) {
        log.info("http GET /annotated-issues?fromYear={}&toYear={}&usernamePattern={}", fromYear, toYear, usernamePattern);
        return delegate.queryAnnotatedIssues(fromYear, toYear, usernamePattern);
    }
}
