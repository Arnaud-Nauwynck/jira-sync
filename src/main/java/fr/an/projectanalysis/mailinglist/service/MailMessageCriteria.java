package fr.an.projectanalysis.mailinglist.service;

import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageCriteriaDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageExtraFieldsDTO;
import fr.an.projectanalysis.util.CritUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Whether a {@link MailMessageDTO} matches the Main/Analysis/Development Work/Personal Interest
 * filter criteria of the mailing-list page (a null criteria matches everything).
 */
public class MailMessageCriteria implements Predicate<MailMessageDTO> {

    private final MailMessageCriteriaDTO c;

    public MailMessageCriteria(MailMessageCriteriaDTO c) {
        this.c = c;
    }

    @Override
    public boolean test(MailMessageDTO msg) {
        if (c == null) {
            return true;
        }
        if (!CritUtils.matchesAny(c.subjectContains, msg.subject)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.bodyContains, msg.bodyText)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.fromContains, msg.from)) {
            return false;
        }
        List<String> toCc = new ArrayList<>();
        if (msg.to != null) {
            toCc.addAll(msg.to);
        }
        if (msg.cc != null) {
            toCc.addAll(msg.cc);
        }
        if (!CritUtils.matchesAny(c.toCcContains, toCc.toArray(String[]::new))) {
            return false;
        }

        MailMessageExtraFieldsDTO annotated = msg.annotated;
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
