package fr.an.jira.rest.dtos;

import lombok.Data;

import java.time.Instant;

@Data
public class JiraSyncStatusDTO {

    /** Start time of the last successful sync run, or null if none has run yet. */
    public Instant lastSyncTime;

}
