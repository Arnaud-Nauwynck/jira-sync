import {MailMessageCriteriaDTO, MailMessageDTO} from '../../rest';
import {Crit} from '../../utils/Crit';

export class MailingListCriteria {

  static anyCriteriaSet(c: MailMessageCriteriaDTO): boolean {
    return Crit.parseCsvList(c.subjectContains).length > 0
      || Crit.parseCsvList(c.bodyContains).length > 0
      || Crit.parseCsvList(c.fromContains).length > 0
      || Crit.parseCsvList(c.toCcContains).length > 0
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

  static match(c: MailMessageCriteriaDTO, msg: MailMessageDTO): boolean {
    if (!Crit.matchesAny(c.subjectContains, msg.subject)) {
      return false;
    }
    if (!Crit.matchesAny(c.bodyContains, msg.bodyText)) {
      return false;
    }
    if (!Crit.matchesAny(c.fromContains, msg.from)) {
      return false;
    }
    if (!Crit.matchesAny(c.toCcContains, ...(msg.to ?? []), ...(msg.cc ?? []))) {
      return false;
    }
    const annotated = msg.annotated;
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
