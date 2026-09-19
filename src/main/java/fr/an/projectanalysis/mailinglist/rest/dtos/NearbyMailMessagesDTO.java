package fr.an.projectanalysis.mailinglist.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Message-IDs of the messages nearest to a given message (by {@code Date}), on the "prev"
 * (earlier) and "next" (later) sides: the nearest message overall, and the nearest one from the
 * same sender (the raw {@code From} header). Any field is null when no such message exists.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NearbyMailMessagesDTO {

    public String prevMail;
    public String prevMailSameSender;

    public String nextMail;
    public String nextMailSameSender;

}
