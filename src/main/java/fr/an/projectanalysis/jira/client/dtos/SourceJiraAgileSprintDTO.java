package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** One sprint of "GET /rest/agile/1.0/board/{id}/sprint". */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraAgileSprintDTO {
    public Long id;
    public String self;
    public String state;
    public String name;
    public String startDate;
    public String endDate;
    public String completeDate;
    public Long originBoardId;
    public String goal;
}
