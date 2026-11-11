package fr.an.projectanalysis.jira.rest.dtos;

import fr.an.projectanalysis.jira.client.dtos.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** All the Jira referential data specific to one project, as fetched from the remote server. */
@Data
public class JiraProjectReferentialDTO {

    public Instant fetchedAt;

    public String projectKey;

    public SourceJiraProjectDTO project;

    // redundant with project.components
    // public List<SourceJiraProjectComponentDTO> components;

    // redundant with project.versions
    // public List<SourceJiraProjectVersionDTO> versions;

    public List<SourceJiraProjectStatusesDTO> statuses;

    // ???? NOT available
    // public List<SourceJiraProjectRoleDTO> roles;

    // ???? NOT available: http 401 need to be login
    // public List<SourceJiraUserDefDTO> assignableUsers;

}
