package fr.an.projectanalysis.github.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A pull request number ("id"), with the last update time ("t") of the pull request. */
@Data
@NoArgsConstructor @AllArgsConstructor
public class GitHubPrIdAndLastUpdateTimeDTO {

    /** Pull request number, as returned by the "query-ids" endpoint. */
    public int id;

    /** Last update time ({@code updatedAt}) of the pull request, in epoch milliseconds; 0 when unknown. */
    public long t;

}
