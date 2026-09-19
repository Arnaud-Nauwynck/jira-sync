package fr.an.projectanalysis.util;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read access to the user-annotated extra fields (Analysis / Development Work / Personal Interest)
 * that are enriched and persisted locally, identically for a Jira issue, a GitHub pull request and a
 * mailing-list message. Implemented by the corresponding "*ExtraFieldsDTO", whose Lombok-generated
 * getters already satisfy it, only so {@link AnnotatedCritUtils} can filter on them once for all 3.
 */
public interface AnnotatedExtraFields {

    String getAnalysisSummary();

    LocalDateTime getAnalysisSummaryLastUpdateTime();

    int getAnalysisSummaryTokensConsumed();

    List<String> getAnalysisUserExtraPrompts();

    String getDevelopmentWorkDescribed();

    LocalDateTime getDevelopmentWorkLastUpdateTime();

    int getDevelopmentWorkTokensConsumed();

    List<String> getDevelopmentWorkUserExtraPrompts();

    String getPersonalInterrestComment();

    Integer getPersonalInterrestPriority10();

}
