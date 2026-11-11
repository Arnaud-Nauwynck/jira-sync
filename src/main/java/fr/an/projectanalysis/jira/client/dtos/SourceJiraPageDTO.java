package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/** Generic paginated response ("values" + paging info), as used by labels and agile endpoints. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraPageDTO<T> {
    public Integer startAt;
    public Integer maxResults;
    public Integer total;
    public Boolean isLast;
    public List<T> values;
}
