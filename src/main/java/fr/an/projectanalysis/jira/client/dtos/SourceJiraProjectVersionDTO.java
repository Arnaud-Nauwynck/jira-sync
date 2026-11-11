package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** One element of "GET /rest/api/2/project/{key}/versions". */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraProjectVersionDTO {
    public String self;
    public String id;
    public String name;
    public String description;
    public Boolean archived;
    public Boolean released;
    public Boolean overdue;
    public String startDate;
    public String releaseDate;
    public String userStartDate;
    public String userReleaseDate;
    public Long projectId;
}
