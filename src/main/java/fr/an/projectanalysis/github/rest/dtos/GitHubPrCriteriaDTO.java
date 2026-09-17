package fr.an.projectanalysis.github.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Filter criteria for {@code GitHubPullRequestService.queryPullRequests}, mirroring the Data
 * Fetching and Main/Analysis/Development Work/Personal Interest filter panels of the
 * github-pull-requests Angular page. Every field is optional; an unset field does not filter
 * on that criterion.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitHubPrCriteriaDTO {

    // Data fetching criteria
    public Integer fromYear;
    public Integer toYear;
    public String usernamePattern;
    public Integer fromPullRequestNumber;
    public Integer toPullRequestNumber;
    public String pullRequestNumberPattern;

    // Main criteria
    public String titleContains;
    public String bodyContains;
    public String authorContains;
    public String labelContains;
    public String baseRefContains;
    public String excludedStates;
    /** Tri-state ('yes'/'no'/'any'): whether the pull request is a draft. */
    public String draftAvailability;
    /** Tri-state ('yes'/'no'/'any'): whether the pull request is merged. */
    public String mergedAvailability;
    /** Tri-state ('yes'/'no'/'any'): whether the pull request is mergeable. */
    public String mergeableAvailability;
    public String mergeableStatePattern;

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
