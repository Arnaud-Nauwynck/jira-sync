package fr.an.projectanalysis.github.rest.dtos;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Extra fields enriched and persisted locally, not coming from the source GitHub server.
 * Mirrors {@code fr.an.jira.rest.dtos.IssueExtraFieldsDTO}.
 */
@Data
public class GitHubPullRequestExtraFieldsDTO {

    public String analysisSummary;
    public LocalDateTime analysisSummaryLastUpdateTime;
    public int analysisSummaryTokensConsumed;
    public List<String> analysisUserExtraPrompts;

    public String personalInterrestComment;
    public Integer personalInterrestPriority10;
}
