package fr.an.projectanalysis.github.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor @AllArgsConstructor
public class GitHubPullRequestAnnotationDTO {

    public int number;
    public GitHubPullRequestExtraFieldsDTO annotated;

}
