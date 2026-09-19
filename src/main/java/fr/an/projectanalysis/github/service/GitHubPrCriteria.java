package fr.an.projectanalysis.github.service;

import fr.an.projectanalysis.github.rest.dtos.GitHubPrCriteriaDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestExtraFieldsDTO;
import fr.an.projectanalysis.util.CritUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Whether a {@link GitHubPullRequestDTO} matches the Main/Analysis/Development Work/Personal
 * Interest filter criteria of the github-pull-requests page (a null criteria matches everything).
 */
public class GitHubPrCriteria implements Predicate<GitHubPullRequestDTO> {

    private final GitHubPrCriteriaDTO c;

    public GitHubPrCriteria(GitHubPrCriteriaDTO c) {
        this.c = c;
    }

    @Override
    public boolean test(GitHubPullRequestDTO pr) {
        if (c == null) {
            return true;
        }
        if (!CritUtils.matchesAny(c.titleContains, pr.title)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.bodyContains, pr.body)) {
            return false;
        }
        List<String> authorValues = new ArrayList<>();
        authorValues.add(pr.authorLogin);
        if (pr.assigneeLogins != null) {
            authorValues.addAll(pr.assigneeLogins);
        }
        if (pr.requestedReviewerLogins != null) {
            authorValues.addAll(pr.requestedReviewerLogins);
        }
        if (!CritUtils.matchesAny(c.authorContains, authorValues.toArray(String[]::new))) {
            return false;
        }
        List<String> labels = pr.labelNames != null ? pr.labelNames : List.of();
        if (!CritUtils.matchesAny(c.labelContains, labels.toArray(String[]::new))) {
            return false;
        }
        if (!CritUtils.matchesAny(c.baseRefContains, pr.baseRef)) {
            return false;
        }
        if (CritUtils.isExcluded(c.excludedStates, pr.state)) {
            return false;
        }
        if (!CritUtils.matchesAvailability(c.draftAvailability, pr.draft)) {
            return false;
        }
        if (!CritUtils.matchesAvailability(c.mergedAvailability, pr.merged)) {
            return false;
        }
        if (!CritUtils.matchesAvailability(c.mergeableAvailability, Boolean.TRUE.equals(pr.mergeable))) {
            return false;
        }
        if (!CritUtils.matchesRegex(c.mergeableStatePattern, pr.mergeableState)) {
            return false;
        }

        GitHubPullRequestExtraFieldsDTO annotated = pr.annotated;
        boolean hasAnalysis = annotated != null && annotated.analysisSummary != null && !annotated.analysisSummary.isBlank();
        if (!CritUtils.matchesAvailability(c.analysisAvailability, hasAnalysis)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.analysisSummaryContains, annotated != null ? annotated.analysisSummary : null)) {
            return false;
        }
        if (!CritUtils.matchesDateRange(c.analysisSummaryUpdatedFrom, c.analysisSummaryUpdatedTo,
                annotated != null ? annotated.analysisSummaryLastUpdateTime : null)) {
            return false;
        }
        if (!CritUtils.matchesTokensRangeK(c.analysisSummaryMinTokensK, c.analysisSummaryMaxTokensK,
                annotated != null ? annotated.analysisSummaryTokensConsumed : 0)) {
            return false;
        }
        List<String> analysisExtraPrompts = annotated != null && annotated.analysisUserExtraPrompts != null
                ? annotated.analysisUserExtraPrompts : List.of();
        if (!CritUtils.matchesAny(c.analysisUserExtraPromptsContains, analysisExtraPrompts.toArray(String[]::new))) {
            return false;
        }

        boolean hasDevWork = annotated != null && annotated.developmentWorkDescribed != null && !annotated.developmentWorkDescribed.isBlank();
        if (!CritUtils.matchesAvailability(c.developmentWorkAvailability, hasDevWork)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.developmentWorkDescribedContains, annotated != null ? annotated.developmentWorkDescribed : null)) {
            return false;
        }
        if (!CritUtils.matchesDateRange(c.developmentWorkUpdatedFrom, c.developmentWorkUpdatedTo,
                annotated != null ? annotated.developmentWorkLastUpdateTime : null)) {
            return false;
        }
        if (!CritUtils.matchesTokensRangeK(c.developmentWorkMinTokensK, c.developmentWorkMaxTokensK,
                annotated != null ? annotated.developmentWorkTokensConsumed : 0)) {
            return false;
        }
        List<String> devWorkExtraPrompts = annotated != null && annotated.developmentWorkUserExtraPrompts != null
                ? annotated.developmentWorkUserExtraPrompts : List.of();
        if (!CritUtils.matchesAny(c.developmentWorkUserExtraPromptsContains, devWorkExtraPrompts.toArray(String[]::new))) {
            return false;
        }

        boolean hasPersonalInterrest = annotated != null && annotated.personalInterrestComment != null && !annotated.personalInterrestComment.isBlank();
        if (!CritUtils.matchesAvailability(c.personalInterrestAvailability, hasPersonalInterrest)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.personalInterrestCommentContains, annotated != null ? annotated.personalInterrestComment : null)) {
            return false;
        }
        if (!CritUtils.matchesNumberRange(c.personalInterrestMinPriority, c.personalInterrestMaxPriority,
                annotated != null ? annotated.personalInterrestPriority10 : null)) {
            return false;
        }
        return true;
    }

}
