package fr.an.jira.rest.dtos;

import lombok.Data;

/**
 * Optional filter criteria for {@code JiraIssueService.queryAnnotatedIssues}, mirroring the
 * filter panels of the issues-list Angular page (Main / Analysis / Development Work / Personal
 * Interest criteria). Every field is optional; an unset field does not filter on that criterion.
 */
@Data
public class JiraIssueQueryCriteriaDTO {

    // Main criteria
    public String summaryContains;
    public String descriptionContains;
    public String authorContains;
    public String commentsContains;
    public String commentAuthorContains;
    public String excludedTypes;
    public String excludedResolutions;
    public String excludedStatuses;
    public String excludedPriorities;
    public String labelsContains;
    /** Tri-state ('yes'/'no'/'any'): whether the issue's labels contain "pull-request-available". */
    public String pullRequestAvailableLabel;

    // Analysis criteria
    public String analysisSummaryContains;
    public String analysisUserExtraPromptsContains;
    public String analysisSummaryUpdatedFrom;
    public String analysisSummaryUpdatedTo;
    public Integer analysisSummaryMinTokensK;
    public Integer analysisSummaryMaxTokensK;
    public String analysisAvailability;

    // Development work criteria
    public String developmentWorkDescribedContains;
    public String developmentWorkUserExtraPromptsContains;
    public String developmentWorkUpdatedFrom;
    public String developmentWorkUpdatedTo;
    public Integer developmentWorkMinTokensK;
    public Integer developmentWorkMaxTokensK;
    public String developmentWorkAvailability;

    // Personal interest criteria
    public String personalInterrestCommentContains;
    public Integer personalInterrestMinPriority;
    public Integer personalInterrestMaxPriority;
    public String personalInterrestAvailability;

}
