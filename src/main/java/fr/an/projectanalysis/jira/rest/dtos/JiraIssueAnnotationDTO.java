package fr.an.projectanalysis.jira.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor @AllArgsConstructor
public class JiraIssueAnnotationDTO {

    public String key;
    public IssueExtraFieldsDTO annotated;

}
