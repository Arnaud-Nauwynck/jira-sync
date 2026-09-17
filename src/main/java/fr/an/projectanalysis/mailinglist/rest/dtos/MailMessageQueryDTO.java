package fr.an.projectanalysis.mailinglist.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** Request body for {@code POST .../mailing-list-messages/query} and {@code .../query-ids}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailMessageQueryDTO {

    public MailMessageCriteriaDTO criteria;

    /** Caps the number of results; defaults to 1000 when unset. */
    public Integer limit;

}
