package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO corresponding to SourceJiraProjectComponentDTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class JiraProjectComponentDTO {

    public String id;
    public String name;
    public String description;

//    public String leadUserKey;

//    public String assigneeType;
//    public String assigneeUserKey;

//    public String realAssigneeType;
//    public String realAssigneeUserKey;

//    public Boolean isAssigneeTypeValid;

}
