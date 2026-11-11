package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Mirrors one element of the JSON array returned by the Jira REST API
 * "GET /rest/api/2/project/{key}/components" endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraProjectComponentDTO {
    public String self;
    public String id;
    public String name;
    public String description;
    public SourceJiraUserDTO lead;
    public String assigneeType;
    public SourceJiraUserDTO assignee;
    public String realAssigneeType;
    public SourceJiraUserDTO realAssignee;
    public Boolean isAssigneeTypeValid;
    public String project;
    public Long projectId;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraUserDTO {
        public String self;
        public String name;
        public String key;
        public String displayName;
        public Boolean active;
    }
}
