package fr.an.projectanalysis.util;

/**
 * Read access to the Analysis / Development Work / Personal Interest filter criteria, identical in the
 * issues-list, github-pull-requests and mailing-list Angular filter panels. Implemented by the
 * corresponding "*CriteriaDTO", whose Lombok-generated getters already satisfy it, only so
 * {@link AnnotatedCritUtils} can evaluate them once for all 3.
 */
public interface AnnotationCriteriaFields {

    String getAnalysisAvailability();

    String getAnalysisSummaryContains();

    String getAnalysisSummaryUpdatedFrom();

    String getAnalysisSummaryUpdatedTo();

    Integer getAnalysisSummaryMinTokensK();

    Integer getAnalysisSummaryMaxTokensK();

    String getAnalysisUserExtraPromptsContains();

    String getDevelopmentWorkAvailability();

    String getDevelopmentWorkDescribedContains();

    String getDevelopmentWorkUpdatedFrom();

    String getDevelopmentWorkUpdatedTo();

    Integer getDevelopmentWorkMinTokensK();

    Integer getDevelopmentWorkMaxTokensK();

    String getDevelopmentWorkUserExtraPromptsContains();

    String getPersonalInterrestAvailability();

    String getPersonalInterrestCommentContains();

    Integer getPersonalInterrestMinPriority();

    Integer getPersonalInterrestMaxPriority();

}
