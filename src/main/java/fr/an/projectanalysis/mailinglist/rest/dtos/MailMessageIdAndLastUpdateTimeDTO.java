package fr.an.projectanalysis.mailinglist.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A message id ("id"), with the last update time ("t") of the message. */
@Data
@NoArgsConstructor @AllArgsConstructor
public class MailMessageIdAndLastUpdateTimeDTO {

    /** Message-ID, as returned by the "query-ids" endpoint. */
    public String id;

    /** Last update time of the message, that is its sent {@code date}, in epoch milliseconds; 0 when unknown. */
    public long t;

}
