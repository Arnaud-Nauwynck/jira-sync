package fr.an.projectanalysis.github.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One slice of a {@link GitHubPrDistributionStatsDTO} pie-chart distribution. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitHubPrDistributionEntryDTO {

    public String name;
    public int count;

}
