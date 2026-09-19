package fr.an.projectanalysis.util;

import java.util.List;

/**
 * Matching of the Analysis / Development Work / Personal Interest filter criteria against the
 * user-annotated extra fields, shared by the "*Criteria" predicate classes of the Jira issues,
 * GitHub pull requests and mailing-list messages, which all expose these same criteria.
 */
public class AnnotatedCritUtils {

    private AnnotatedCritUtils() {
    }

    /** Whether the annotated extra fields (possibly null, when the record was never annotated) match all
     * of the Analysis, Development Work and Personal Interest criteria. */
    public static boolean matchesAnnotations(AnnotationCriteriaFields c, AnnotatedExtraFields annotated) {
        return matchesAnalysis(c, annotated)
                && matchesDevelopmentWork(c, annotated)
                && matchesPersonalInterrest(c, annotated);
    }

    private static boolean matchesAnalysis(AnnotationCriteriaFields c, AnnotatedExtraFields annotated) {
        String summary = annotated != null ? annotated.getAnalysisSummary() : null;
        if (!CritUtils.matchesAvailability(c.getAnalysisAvailability(), summary != null && !summary.isBlank())) {
            return false;
        }
        if (!CritUtils.matchesAny(c.getAnalysisSummaryContains(), summary)) {
            return false;
        }
        if (!CritUtils.matchesDateRange(c.getAnalysisSummaryUpdatedFrom(), c.getAnalysisSummaryUpdatedTo(),
                annotated != null ? annotated.getAnalysisSummaryLastUpdateTime() : null)) {
            return false;
        }
        if (!CritUtils.matchesTokensRangeK(c.getAnalysisSummaryMinTokensK(), c.getAnalysisSummaryMaxTokensK(),
                annotated != null ? annotated.getAnalysisSummaryTokensConsumed() : 0)) {
            return false;
        }
        List<String> extraPrompts = annotated != null && annotated.getAnalysisUserExtraPrompts() != null
                ? annotated.getAnalysisUserExtraPrompts() : List.of();
        return CritUtils.matchesAny(c.getAnalysisUserExtraPromptsContains(), extraPrompts.toArray(String[]::new));
    }

    private static boolean matchesDevelopmentWork(AnnotationCriteriaFields c, AnnotatedExtraFields annotated) {
        String described = annotated != null ? annotated.getDevelopmentWorkDescribed() : null;
        if (!CritUtils.matchesAvailability(c.getDevelopmentWorkAvailability(), described != null && !described.isBlank())) {
            return false;
        }
        if (!CritUtils.matchesAny(c.getDevelopmentWorkDescribedContains(), described)) {
            return false;
        }
        if (!CritUtils.matchesDateRange(c.getDevelopmentWorkUpdatedFrom(), c.getDevelopmentWorkUpdatedTo(),
                annotated != null ? annotated.getDevelopmentWorkLastUpdateTime() : null)) {
            return false;
        }
        if (!CritUtils.matchesTokensRangeK(c.getDevelopmentWorkMinTokensK(), c.getDevelopmentWorkMaxTokensK(),
                annotated != null ? annotated.getDevelopmentWorkTokensConsumed() : 0)) {
            return false;
        }
        List<String> extraPrompts = annotated != null && annotated.getDevelopmentWorkUserExtraPrompts() != null
                ? annotated.getDevelopmentWorkUserExtraPrompts() : List.of();
        return CritUtils.matchesAny(c.getDevelopmentWorkUserExtraPromptsContains(), extraPrompts.toArray(String[]::new));
    }

    private static boolean matchesPersonalInterrest(AnnotationCriteriaFields c, AnnotatedExtraFields annotated) {
        String comment = annotated != null ? annotated.getPersonalInterrestComment() : null;
        if (!CritUtils.matchesAvailability(c.getPersonalInterrestAvailability(), comment != null && !comment.isBlank())) {
            return false;
        }
        if (!CritUtils.matchesAny(c.getPersonalInterrestCommentContains(), comment)) {
            return false;
        }
        return CritUtils.matchesNumberRange(c.getPersonalInterrestMinPriority(), c.getPersonalInterrestMaxPriority(),
                annotated != null ? annotated.getPersonalInterrestPriority10() : null);
    }

}
