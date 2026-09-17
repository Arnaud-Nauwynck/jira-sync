import {GitHubPrCriteriaDTO, GitHubPullRequestDTO} from '../../rest';
import {Crit} from '../../utils/Crit';

export class GithubPrCriteria {

  static anyCriteriaSet(c: GitHubPrCriteriaDTO): boolean {
    return c.fromYear != null
      || c.toYear != null
      || (c.usernamePattern ?? '').trim().length > 0
      || c.fromPullRequestNumber != null
      || c.toPullRequestNumber != null
      || (c.pullRequestNumberPattern ?? '').trim().length > 0
      || Crit.parseCsvList(c.titleContains).length > 0
      || Crit.parseCsvList(c.bodyContains).length > 0
      || Crit.parseCsvList(c.authorContains).length > 0
      || Crit.parseCsvList(c.labelContains).length > 0
      || Crit.parseCsvList(c.baseRefContains).length > 0
      || Crit.parseCsvList(c.excludedStates).length > 0
      || (c.draftAvailability ?? 'any') !== 'any'
      || (c.mergedAvailability ?? 'any') !== 'any'
      || (c.mergeableAvailability ?? 'any') !== 'any'
      || (c.mergeableStatePattern ?? '').trim().length > 0
      || Crit.parseCsvList(c.analysisSummaryContains).length > 0
      || (c.analysisSummaryUpdatedFrom ?? '').length > 0
      || (c.analysisSummaryUpdatedTo ?? '').length > 0
      || c.analysisSummaryMinTokensK != null
      || c.analysisSummaryMaxTokensK != null
      || Crit.parseCsvList(c.analysisUserExtraPromptsContains).length > 0
      || (c.analysisAvailability ?? 'any') !== 'any'
      || Crit.parseCsvList(c.developmentWorkDescribedContains).length > 0
      || (c.developmentWorkUpdatedFrom ?? '').length > 0
      || (c.developmentWorkUpdatedTo ?? '').length > 0
      || c.developmentWorkMinTokensK != null
      || c.developmentWorkMaxTokensK != null
      || Crit.parseCsvList(c.developmentWorkUserExtraPromptsContains).length > 0
      || (c.developmentWorkAvailability ?? 'any') !== 'any'
      || Crit.parseCsvList(c.personalInterrestCommentContains).length > 0
      || c.personalInterrestMinPriority != null
      || c.personalInterrestMaxPriority != null
      || (c.personalInterrestAvailability ?? 'any') !== 'any';
  }

  static match(c: GitHubPrCriteriaDTO, pr: GitHubPullRequestDTO): boolean {
    if (!Crit.matchesDateTextInYearRange(c.fromYear, c.toYear, pr.createdAt)) {
      return false;
    }
    if (c.usernamePattern) {
      const name = (pr.authorLogin && pr.authorLogin.trim()) ? pr.authorLogin : 'unknown';
      if (!Crit.matchesRegex(c.usernamePattern, name)) {
        return false;
      }
    }
    if (!Crit.matchesRegex(c.pullRequestNumberPattern, pr.number != null ? String(pr.number) : undefined)) {
      return false;
    }
    if (!Crit.matchesNumberRange(c.fromPullRequestNumber, c.toPullRequestNumber, pr.number)) {
      return false;
    }
    if (!Crit.matchesAny(c.titleContains, pr.title)) {
      return false;
    }
    if (!Crit.matchesAny(c.bodyContains, pr.body)) {
      return false;
    }
    if (!Crit.matchesAny(c.authorContains, pr.authorLogin, ...(pr.assigneeLogins ?? []), ...(pr.requestedReviewerLogins ?? []))) {
      return false;
    }
    if (!Crit.matchesAny(c.labelContains, ...(pr.labelNames ?? []))) {
      return false;
    }
    if (!Crit.matchesAny(c.baseRefContains, pr.baseRef)) {
      return false;
    }
    if (pr.state != null && Crit.isExcluded(c.excludedStates, pr.state)) {
      return false;
    }
    if (!Crit.matchesYesNoAny(c.draftAvailability, !!pr.draft)) {
      return false;
    }
    if (!Crit.matchesYesNoAny(c.mergedAvailability, !!pr.merged)) {
      return false;
    }
    if (!Crit.matchesYesNoAny(c.mergeableAvailability, !!pr.mergeable)) {
      return false;
    }
    if (!Crit.matchesRegex(c.mergeableStatePattern, pr.mergeableState)) {
      return false;
    }
    const annotated = pr.annotated;
    if (!Crit.matchesYesNoAny(c.analysisAvailability, !!annotated?.analysisSummary)) {
      return false;
    }
    if (!Crit.matchesAny(c.analysisSummaryContains, annotated?.analysisSummary)) {
      return false;
    }
    if (!Crit.matchesDateRange(c.analysisSummaryUpdatedFrom, c.analysisSummaryUpdatedTo, annotated?.analysisSummaryLastUpdateTime)) {
      return false;
    }
    if (!Crit.matchesTokensRangeK(c.analysisSummaryMinTokensK, c.analysisSummaryMaxTokensK, annotated?.analysisSummaryTokensConsumed)) {
      return false;
    }
    if (!Crit.matchesAny(c.analysisUserExtraPromptsContains, ...(annotated?.analysisUserExtraPrompts ?? []))) {
      return false;
    }
    if (!Crit.matchesYesNoAny(c.developmentWorkAvailability, !!annotated?.developmentWorkDescribed)) {
      return false;
    }
    if (!Crit.matchesAny(c.developmentWorkDescribedContains, annotated?.developmentWorkDescribed)) {
      return false;
    }
    if (!Crit.matchesDateRange(c.developmentWorkUpdatedFrom, c.developmentWorkUpdatedTo, annotated?.developmentWorkLastUpdateTime)) {
      return false;
    }
    if (!Crit.matchesTokensRangeK(c.developmentWorkMinTokensK, c.developmentWorkMaxTokensK, annotated?.developmentWorkTokensConsumed)) {
      return false;
    }
    if (!Crit.matchesAny(c.developmentWorkUserExtraPromptsContains, ...(annotated?.developmentWorkUserExtraPrompts ?? []))) {
      return false;
    }
    if (!Crit.matchesYesNoAny(c.personalInterrestAvailability, !!annotated?.personalInterrestComment)) {
      return false;
    }
    if (!Crit.matchesAny(c.personalInterrestCommentContains, annotated?.personalInterrestComment)) {
      return false;
    }
    if (!Crit.matchesNumberRange(c.personalInterrestMinPriority, c.personalInterrestMaxPriority, annotated?.personalInterrestPriority10)) {
      return false;
    }
    return true;
  }

}
