package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Response of "GET /rest/api/2/project/{key}". */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraProjectDTO {
    public String expand;
    public String self;
    public String id;
    public String key;
    public String name;
    public String description;
    public String url;
    public String email;
    public String assigneeType;
    public String projectTypeKey;
    public Boolean archived;
    public SourceJiraUserDefDTO lead;
    public SourceJiraProjectCategoryDefDTO projectCategory;
    public List<SourceJiraProjectComponentDTO> components;
    public List<SourceJiraIssueTypeDefDTO> issueTypes;
    public List<SourceJiraProjectVersionDTO> versions;
    /** role name -> role url */
    public Map<String, String> roles;
    public Map<String, String> avatarUrls;
}
