package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.an.projectanalysis.util.AnnotatedExtraFields;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Extra fields enriched and persisted locally, not coming from the source Jira server.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IssueExtraFieldsDTO implements AnnotatedExtraFields {

    public String analysisSummary;
    public LocalDateTime analysisSummaryLastUpdateTime;
    public int analysisSummaryTokensConsumed;
    public List<String> analysisUserExtraPrompts;
    // public String analysisAgentSessionTranscript;

    public String developmentWorkDescribed;
    public LocalDateTime developmentWorkLastUpdateTime;
    public int developmentWorkTokensConsumed;
    public List<String> developmentWorkUserExtraPrompts;
    // public String developmentWorkAgentSessionTranscript;

    public String personalInterrestComment;
    public Integer personalInterrestPriority10;
}
