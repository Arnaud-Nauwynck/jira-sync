import {IssuesCriteriaDTO, JiraIssueDTO} from '../../rest';
import {Crit, Restrictiveness} from '../../utils/Crit';
import {
  KNOWN_RESOLUTIONS,
  KNOWN_TYPES,
  OTHER_RESOLUTIONS, OTHER_TYPES,
  PULL_REQUEST_AVAILABLE_LABEL
} from '../issues-list-view/issues-list-view';

export class IssueCriteria {

  static anyCriteriaSet(c: IssuesCriteriaDTO) {
    return c.fromYear != null
      || c.toYear != null
      || (c.usernamePattern ?? '').trim().length > 0
      || c.fromNumber != null
      || c.toNumber != null
      || (c.keyPattern ?? '').trim().length > 0
      || Crit.parseCsvList(c.summaryContains).length > 0
      || Crit.parseCsvList(c.descriptionContains).length > 0
      || Crit.parseCsvList(c.authorContains).length > 0
      || Crit.parseCsvList(c.commentsContains).length > 0
      || Crit.parseCsvList(c.excludedResolutions).length > 0
      || Crit.parseCsvList(c.excludedStatuses).length > 0
      || Crit.parseCsvList(c.excludedPriorities).length > 0
      || Crit.parseCsvList(c.excludedTypes).length > 0
      || Crit.parseCsvList(c.commentAuthorContains).length > 0
      || Crit.parseCsvList(c.labelsContains).length > 0
      || (c.pullRequestAvailableLabel ?? 'any') !== 'any'
      || Crit.parseCsvList(c.componentsContains).length > 0
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

  public static match(c: IssuesCriteriaDTO, src: JiraIssueDTO): boolean {
    const fields = src.fields;
    if (!fields) {
      return true;
    }
    if (!Crit.matchesDateTextInYearRange(c.fromYear, c.toYear, fields.created)) {
      return false;
    }

    if (c.usernamePattern) {
      const creator = fields.creator;
      const reporter = fields.reporter;
      const name = (creator && creator.trim()) ? creator : ((reporter && reporter.trim()) ? reporter : 'unknown');
      if (! Crit.matchesRegex(c.usernamePattern, name)) {
        return false;
      }
    }
    if (!Crit.matchesRegex(c.keyPattern, src.key)) {
      return false;
    }
    if (!IssueCriteria.matchesKeyNumberRange(c.fromNumber, c.toNumber, src.key)) {
      return false;
    }
    if (!Crit.matchesAny(c.summaryContains, fields.summary)) {
      return false;
    }
    if (!Crit.matchesAny(c.descriptionContains, fields.description)) {
      return false;
    }
    if (!Crit.matchesAny(c.authorContains, fields.creator, fields.reporter)) {
      return false;
    }
    if (Crit.isExcluded(c.excludedResolutions, IssueCriteria.resolutionFilterKey(fields.resolution))) {
      return false;
    }
    if (fields.status != null && Crit.isExcluded(c.excludedStatuses, fields.status)) {
      return false;
    }
    if (fields.priority != null && Crit.isExcluded(c.excludedPriorities, fields.priority)) {
      return false;
    }
    if (Crit.isExcluded(c.excludedTypes, IssueCriteria.typeFilterKey(fields.issuetype))) {
      return false;
    }
    const labels = fields.labels ?? [];
    if (!Crit.matchesAny(c.labelsContains, ...labels)) {
      return false;
    }
    if (!Crit.matchesYesNoAny(c.pullRequestAvailableLabel, labels.includes(PULL_REQUEST_AVAILABLE_LABEL))) {
      return false;
    }
    const components = fields.components ?? [];
    if (!Crit.matchesAny(c.componentsContains, ...components)) {
      return false;
    }
    const comments = fields.comments ?? [];
    if (!Crit.matchesAny(c.commentsContains, ...comments.map((cm) => cm.body))) {
      return false;
    }
    if (!Crit.matchesAny(c.commentAuthorContains, ...comments.map((cm) => cm.author))) {
      return false;
    }
    const annotated = src.annotated;
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


  /** Maps an issue type to itself if it is a known enum option, or to the "(others)" bucket otherwise. */
  private static typeFilterKey(type: string | undefined): string {
    const value = type ?? '';
    return KNOWN_TYPES.has(value) ? value : OTHER_TYPES;
  }

  /** Maps a resolution value to itself if it is a known enum option, or to the "(others)" bucket otherwise. */
  private static resolutionFilterKey(resolution: string | undefined): string {
    const value = resolution ?? '';
    return KNOWN_RESOLUTIONS.has(value) ? value : OTHER_RESOLUTIONS;
  }

  /** Whether the numeric suffix of the key (eg "123" in "PROJ-123") falls within [fromNumber, toNumber] (inclusive, either bound optional). */
  public static matchesKeyNumberRange(fromNumber: number | undefined, toNumber: number | undefined, key: string | undefined): boolean {
    if (fromNumber == null && toNumber == null) {
      return true;
    }
    const number = IssueCriteria.issueNumberOf(key);
    if (number == null) {
      return false;
    }
    if (fromNumber != null && number < fromNumber) {
      return false;
    }
    if (toNumber != null && number > toNumber) {
      return false;
    }
    return true;
  }

  public static issueNumberOf(key: string | undefined): number | undefined {
    if (!key) {
      return undefined;
    }
    const dashIdx = key.lastIndexOf('-');
    if (dashIdx < 0 || dashIdx === key.length - 1) {
      return undefined;
    }
    const n = Number(key.substring(dashIdx + 1));
    return Number.isFinite(n) ? n : undefined;
  }


}
