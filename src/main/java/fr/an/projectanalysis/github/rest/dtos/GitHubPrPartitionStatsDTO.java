package fr.an.projectanalysis.github.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Count of locally-synced pull requests per "created_year" partition. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitHubPrPartitionStatsDTO {

    public List<YearCountDTO> statsPerYear;

}
