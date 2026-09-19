package fr.an.projectanalysis.mailinglist.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.an.projectanalysis.util.AnnotatedExtraFields;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Extra fields enriched and persisted locally, not coming from the source mailing-list archive.
 * Mirrors {@code fr.an.jira.rest.dtos.IssueExtraFieldsDTO}.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailMessageExtraFieldsDTO implements AnnotatedExtraFields {

    public String analysisSummary;
    public LocalDateTime analysisSummaryLastUpdateTime;
    public int analysisSummaryTokensConsumed;
    public List<String> analysisUserExtraPrompts;

    public String developmentWorkDescribed;
    public LocalDateTime developmentWorkLastUpdateTime;
    public int developmentWorkTokensConsumed;
    public List<String> developmentWorkUserExtraPrompts;

    public String personalInterrestComment;
    public Integer personalInterrestPriority10;
}
