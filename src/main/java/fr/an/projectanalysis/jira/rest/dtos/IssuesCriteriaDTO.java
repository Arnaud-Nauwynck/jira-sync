package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.an.projectanalysis.util.AnnotationCriteriaFields;
import lombok.Data;

/**
 * Filter criteria for {@code JiraIssueService.queryAnnotatedIssues}, mirroring the Data Fetching
 * and Main/Analysis/Development Work/Personal Interest filter panels of the issues-list Angular
 * page. Every field is optional; an unset field does not filter on that criterion.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IssuesCriteriaDTO implements AnnotationCriteriaFields {

    // Data fetching criteria
    public Integer fromYear;
    public Integer toYear;
    public String usernamePattern;
    public Integer fromNumber;
    public Integer toNumber;
    public String keyPattern;

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
    public String componentsContains;

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
