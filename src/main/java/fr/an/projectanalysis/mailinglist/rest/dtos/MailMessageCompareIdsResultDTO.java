package fr.an.projectanalysis.mailinglist.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Result of {@code POST .../mailing-list-messages/compare-query-ids}: the Message-IDs matched by only one side, or by both. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailMessageCompareIdsResultDTO {

    /** Message-IDs matched by the left criteria only, in left query order. */
    public List<String> leftOnlyIds;

    /** Message-IDs matched by both criteria, in left query order; filled only when {@code fillCommonIds} was requested. */
    public List<String> commonIds;

    /** Count of Message-IDs matched by both criteria, always filled (even when {@code commonIds} is not). */
    public int commonCount;

    /** Message-IDs matched by the right criteria only, in right query order. */
    public List<String> rightOnlyIds;

}
