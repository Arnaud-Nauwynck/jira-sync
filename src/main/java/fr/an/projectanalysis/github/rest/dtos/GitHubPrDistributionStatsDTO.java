package fr.an.projectanalysis.github.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Count of locally-synced pull requests grouped by the requested dimension (state, author,
 * label, base branch, or mergeable state), plus a per-user breakdown, for display as PieCharts. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitHubPrDistributionStatsDTO {

    public String dimension;
    public List<GitHubPrDistributionEntryDTO> entries;

    /** Count of the same PRs grouped by author login, regardless of {@link #dimension}. */
    public List<GitHubPrDistributionEntryDTO> entriesPerUser;

}
