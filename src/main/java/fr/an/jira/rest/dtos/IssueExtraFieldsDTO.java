package fr.an.jira.rest.dtos;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Extra fields enriched and persisted locally, not coming from the source Jira server.
 */
@Data
public class IssueExtraFieldsDTO {

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
    public int personalInterrestPriority10;
}
