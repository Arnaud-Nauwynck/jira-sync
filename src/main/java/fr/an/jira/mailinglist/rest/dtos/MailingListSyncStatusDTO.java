package fr.an.jira.mailinglist.rest.dtos;

import lombok.Data;

import java.time.YearMonth;

@Data
public class MailingListSyncStatusDTO {

    /** Last month ("yyyy-MM") considered fully archived (no longer re-fetched), or null if none has synced yet. */
    public YearMonth lastClosedMonth;

}
