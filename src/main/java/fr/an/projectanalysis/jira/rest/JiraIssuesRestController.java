package fr.an.projectanalysis.jira.rest;

import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueQueryCriteriaDTO;
import fr.an.projectanalysis.jira.rest.dtos.UserJiraIssueStatsDTO;
import fr.an.projectanalysis.jira.service.JiraIssueService;
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
    @GetMapping("/by-key/{key}")
    public ResponseEntity<JiraIssueDTO> findIssueByKey(@PathVariable("key") String key) {
        log.info("http GET {}/by-key/{}", BASE_URL, key);
        JiraIssueDTO found = delegate.findAnnotatedIssueByKey(key);
        return found != null ? ResponseEntity.ok(found) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "List issues created between fromYear and toYear (inclusive), optionally filtered by creator username, issue number range, "
            + "key pattern, and/or the Main/Analysis/Development Work/Personal Interest filter criteria of the issues-list page")
    @GetMapping("/by-query")
    public Collection<JiraIssueDTO> queryIssues(
            @RequestParam(name="fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name="toYear", defaultValue = "2050") int toYear,
            @RequestParam(name="usernamePattern", required = false) String usernamePattern,
            @RequestParam(name="fromNumber", required = false) Integer fromNumber,
            @RequestParam(name="toNumber", required = false) Integer toNumber,
            @RequestParam(name="keyPattern", required = false) String keyPattern,

            @RequestParam(name="summaryContains", required = false) String summaryContains,
            @RequestParam(name="descriptionContains", required = false) String descriptionContains,
            @RequestParam(name="authorContains", required = false) String authorContains,
            @RequestParam(name="commentsContains", required = false) String commentsContains,
            @RequestParam(name="commentAuthorContains", required = false) String commentAuthorContains,
            @RequestParam(name="excludedTypes", required = false) String excludedTypes,
            @RequestParam(name="excludedResolutions", required = false) String excludedResolutions,
            @RequestParam(name="excludedStatuses", required = false) String excludedStatuses,
            @RequestParam(name="excludedPriorities", required = false) String excludedPriorities,
            @RequestParam(name="labelsContains", required = false) String labelsContains,
            @RequestParam(name="pullRequestAvailableLabel", required = false) String pullRequestAvailableLabel,
            @RequestParam(name="componentsContains", required = false) String componentsContains,

            @RequestParam(name="analysisSummaryContains", required = false) String analysisSummaryContains,
            @RequestParam(name="analysisUserExtraPromptsContains", required = false) String analysisUserExtraPromptsContains,
            @RequestParam(name="analysisSummaryUpdatedFrom", required = false) String analysisSummaryUpdatedFrom,
            @RequestParam(name="analysisSummaryUpdatedTo", required = false) String analysisSummaryUpdatedTo,
            @RequestParam(name="analysisSummaryMinTokensK", required = false) Integer analysisSummaryMinTokensK,
            @RequestParam(name="analysisSummaryMaxTokensK", required = false) Integer analysisSummaryMaxTokensK,
            @RequestParam(name="analysisAvailability", required = false) String analysisAvailability,

            @RequestParam(name="developmentWorkDescribedContains", required = false) String developmentWorkDescribedContains,
            @RequestParam(name="developmentWorkUserExtraPromptsContains", required = false) String developmentWorkUserExtraPromptsContains,
            @RequestParam(name="developmentWorkUpdatedFrom", required = false) String developmentWorkUpdatedFrom,
            @RequestParam(name="developmentWorkUpdatedTo", required = false) String developmentWorkUpdatedTo,
            @RequestParam(name="developmentWorkMinTokensK", required = false) Integer developmentWorkMinTokensK,
            @RequestParam(name="developmentWorkMaxTokensK", required = false) Integer developmentWorkMaxTokensK,
            @RequestParam(name="developmentWorkAvailability", required = false) String developmentWorkAvailability,

            @RequestParam(name="personalInterrestCommentContains", required = false) String personalInterrestCommentContains,
            @RequestParam(name="personalInterrestMinPriority", required = false) Integer personalInterrestMinPriority,
            @RequestParam(name="personalInterrestMaxPriority", required = false) Integer personalInterrestMaxPriority,
            @RequestParam(name="personalInterrestAvailability", required = false) String personalInterrestAvailability
    ) {
        log.info("http GET {}/by-query?fromYear={}&toYear={}&usernamePattern={}&fromNumber={}&toNumber={}&keyPattern={}&...",
                BASE_URL, fromYear, toYear, usernamePattern, fromNumber, toNumber, keyPattern);
        JiraIssueQueryCriteriaDTO criteria = new JiraIssueQueryCriteriaDTO();
        criteria.setSummaryContains(summaryContains);
        criteria.setDescriptionContains(descriptionContains);
        criteria.setAuthorContains(authorContains);
        criteria.setCommentsContains(commentsContains);
        criteria.setCommentAuthorContains(commentAuthorContains);
        criteria.setExcludedTypes(excludedTypes);
        criteria.setExcludedResolutions(excludedResolutions);
        criteria.setExcludedStatuses(excludedStatuses);
        criteria.setExcludedPriorities(excludedPriorities);
        criteria.setLabelsContains(labelsContains);
        criteria.setPullRequestAvailableLabel(pullRequestAvailableLabel);
        criteria.setComponentsContains(componentsContains);
        criteria.setAnalysisSummaryContains(analysisSummaryContains);
        criteria.setAnalysisUserExtraPromptsContains(analysisUserExtraPromptsContains);
        criteria.setAnalysisSummaryUpdatedFrom(analysisSummaryUpdatedFrom);
        criteria.setAnalysisSummaryUpdatedTo(analysisSummaryUpdatedTo);
        criteria.setAnalysisSummaryMinTokensK(analysisSummaryMinTokensK);
        criteria.setAnalysisSummaryMaxTokensK(analysisSummaryMaxTokensK);
        criteria.setAnalysisAvailability(analysisAvailability);
        criteria.setDevelopmentWorkDescribedContains(developmentWorkDescribedContains);
        criteria.setDevelopmentWorkUserExtraPromptsContains(developmentWorkUserExtraPromptsContains);
        criteria.setDevelopmentWorkUpdatedFrom(developmentWorkUpdatedFrom);
        criteria.setDevelopmentWorkUpdatedTo(developmentWorkUpdatedTo);
        criteria.setDevelopmentWorkMinTokensK(developmentWorkMinTokensK);
        criteria.setDevelopmentWorkMaxTokensK(developmentWorkMaxTokensK);
        criteria.setDevelopmentWorkAvailability(developmentWorkAvailability);
        criteria.setPersonalInterrestCommentContains(personalInterrestCommentContains);
        criteria.setPersonalInterrestMinPriority(personalInterrestMinPriority);
        criteria.setPersonalInterrestMaxPriority(personalInterrestMaxPriority);
        criteria.setPersonalInterrestAvailability(personalInterrestAvailability);
        return delegate.queryAnnotatedIssues(fromYear, toYear, usernamePattern, fromNumber, toNumber, keyPattern, criteria);
    }

}
