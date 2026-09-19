package fr.an.projectanalysis.mailinglist.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.an.projectanalysis.util.AnnotationCriteriaFields;
import lombok.Data;

/**
 * Filter criteria for {@code MailMessageService.queryMessages}, mirroring the Data Fetching and
 * Main/Analysis/Development Work/Personal Interest filter panels of the mailing-list Angular
 * page. Every field is optional; an unset field does not filter on that criterion.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailMessageCriteriaDTO implements AnnotationCriteriaFields {

    // Data fetching criteria
    /** 'yyyy-MM', inclusive. */
    public String fromMonth;
    /** 'yyyy-MM', inclusive. */
    public String toMonth;
    public String fromPattern;
    public String subjectPattern;
    public String bodyPattern;
    public String messageIdPattern;

    // Main criteria
    public String subjectContains;
    public String bodyContains;
    public String fromContains;
    public String toCcContains;

    // Analysis criteria
    public String analysisSummaryContains;
    public String analysisUserExtraPromptsContains;
    public String analysisSummaryUpdatedFrom;
    public String analysisSummaryUpdatedTo;
    public Integer analysisSummaryMinTokensK;
    public Integer analysisSummaryMaxTokensK;
    public String analysisAvailability;

    // Development work criteria
    public String developmentWorkDescribedContains;
    public String developmentWorkUserExtraPromptsContains;
    public String developmentWorkUpdatedFrom;
    public String developmentWorkUpdatedTo;
    public Integer developmentWorkMinTokensK;
    public Integer developmentWorkMaxTokensK;
    public String developmentWorkAvailability;

    // Personal interest criteria
    public String personalInterrestCommentContains;
    public Integer personalInterrestMinPriority;
    public Integer personalInterrestMaxPriority;
    public String personalInterrestAvailability;

}
