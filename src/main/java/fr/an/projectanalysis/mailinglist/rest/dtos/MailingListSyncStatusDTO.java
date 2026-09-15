package fr.an.projectanalysis.mailinglist.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.YearMonth;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailingListSyncStatusDTO {

    /** Last month ("yyyy-MM") considered fully archived (no longer re-fetched), or null if none has synced yet. */
    public YearMonth lastClosedMonth;

}
