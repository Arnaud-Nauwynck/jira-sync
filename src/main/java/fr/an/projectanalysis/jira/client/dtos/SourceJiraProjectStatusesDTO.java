package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** One element of "GET /rest/api/2/project/{key}/statuses": an issue type with its allowed statuses. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraProjectStatusesDTO {
    public String self;
    public String id;
    public String name;
    public Boolean subtask;
    public List<SourceJiraStatusDefDTO> statuses;
}
