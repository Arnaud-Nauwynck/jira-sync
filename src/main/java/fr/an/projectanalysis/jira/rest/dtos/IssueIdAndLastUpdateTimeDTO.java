package fr.an.projectanalysis.jira.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An issue key ("id"), with the last update time ("t") of the issue. */
@Data
@NoArgsConstructor @AllArgsConstructor
public class IssueIdAndLastUpdateTimeDTO {

    /** Issue key, as returned by the "query-ids" endpoint. */
    public String id;

    /** Last update time of the issue, in epoch milliseconds; 0 when unknown. */
    public long t;

}
