package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JiraSyncStatusDTO {

    /** Start time of the last successful sync run, or null if none has run yet. */
    public Instant lastSyncTime;

}
