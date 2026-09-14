package fr.an.jira.rest;

import fr.an.jira.rest.dtos.JiraIssueDTO;
import fr.an.jira.rest.dtos.UserJiraIssueStatsDTO;
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

    private static final String BASE_URL = "/api/v1/jira-issues";

    private final JiraIssueService delegate;

    public JiraIssuesRestController(JiraIssueService delegate) {
        this.delegate = delegate;
    }

    @Operation(summary = "Count issues created per user, for issues created between fromYear and toYear (inclusive), optionally filtered by summary/description/comment criteria")
    @GetMapping("/user-issue-create-stats")
    public Collection<UserJiraIssueStatsDTO> queryUserIssueCreateStats(
            @RequestParam(name="fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name="toYear", defaultValue = "2050") int toYear,
            @RequestParam(name="usernamePattern") String usernamePattern,
            @RequestParam(name="summaryPattern", required = false) String summaryPattern,
            @RequestParam(name="descriptionPattern", required = false) String descriptionPattern,
            @RequestParam(name="commentPattern", required = false) String commentPattern,
            @RequestParam(name="commentAuthorPattern", required = false) String commentAuthorPattern
    ) {
        log.info("http GET {}/user-issue-create-stats?fromYear={}&toYear={}&usernamePattern={}&summaryPattern={}&descriptionPattern={}&commentPattern={}&commentAuthorPattern={}",
                BASE_URL, fromYear, toYear, usernamePattern, summaryPattern, descriptionPattern, commentPattern, commentAuthorPattern);
        return delegate.queryUserIssueStats(fromYear, toYear, usernamePattern,
                summaryPattern, descriptionPattern, commentPattern, commentAuthorPattern);
    }

    @Operation(summary = "Find a single issue by its key")
    @GetMapping("/annotated-issues/{key}")
    public ResponseEntity<JiraIssueDTO> findAnnotatedIssueByKey(@PathVariable("key") String key) {
        log.info("http GET {}/annotated-issues/{}", BASE_URL, key);
        JiraIssueDTO found = delegate.findAnnotatedIssueByKey(key);
        return found != null ? ResponseEntity.ok(found) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "List issues created between fromYear and toYear (inclusive), optionally filtered by creator username, issue number range, and/or key pattern")
    @GetMapping("/annotated-issues")
    public Collection<JiraIssueDTO> queryAnnotatedIssues(
            @RequestParam(name="fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name="toYear", defaultValue = "2050") int toYear,
            @RequestParam(name="usernamePattern", required = false) String usernamePattern,
            @RequestParam(name="fromNumber", required = false) Integer fromNumber,
            @RequestParam(name="toNumber", required = false) Integer toNumber,
            @RequestParam(name="keyPattern", required = false) String keyPattern
    ) {
        log.info("http GET {}/annotated-issues?fromYear={}&toYear={}&usernamePattern={}&fromNumber={}&toNumber={}&keyPattern={}",
                BASE_URL, fromYear, toYear, usernamePattern, fromNumber, toNumber, keyPattern);
        return delegate.queryAnnotatedIssues(fromYear, toYear, usernamePattern, fromNumber, toNumber, keyPattern);
    }

}
