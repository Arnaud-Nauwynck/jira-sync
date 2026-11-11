package fr.an.projectanalysis.jira.rest.dtos;

import fr.an.projectanalysis.jira.client.dtos.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** All the Jira global (not project-specific) referential data, as fetched from the remote server. */
@Data
public class JiraAllGlobalReferentialDTO {

    public Instant fetchedAt;

    public List<SourceJiraIssueTypeDefDTO> issueTypes;
    public List<SourceJiraPriorityDefDTO> priorities;
    public List<SourceJiraResolutionDefDTO> resolutions;
    public List<SourceJiraStatusDefDTO> statuses;
    public List<SourceJiraStatusCategoryDefDTO> statusCategories;
    public List<SourceJiraLinkTypeDefDTO> issueLinkTypes;
    public List<SourceJiraFieldDefDTO> fields;
    public List<String> labels;
    public List<SourceJiraProjectCategoryDefDTO> projectCategories;

}
