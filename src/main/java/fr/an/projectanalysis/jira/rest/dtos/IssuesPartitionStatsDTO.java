package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Count of locally-synced issues per "created_year" partition. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IssuesPartitionStatsDTO {

    public List<YearCountDTO> statsPerYear;

}
