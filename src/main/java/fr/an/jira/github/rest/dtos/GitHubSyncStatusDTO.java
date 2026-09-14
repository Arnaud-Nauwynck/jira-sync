package fr.an.jira.github.rest.dtos;

import lombok.Data;

import java.time.Instant;

@Data
public class GitHubSyncStatusDTO {

    /** Start time of the last successful sync run, or null if none has run yet. */
    public Instant lastSyncTime;

}
