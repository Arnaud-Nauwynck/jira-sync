package fr.an.projectanalysis.github.rest.dtos;

import lombok.Data;

/**
 * Flattened copy of {@code fr.an.projectanalysis.github.client.dtos.SourceGitHubIssueCommentDTO}
 * (a GitHub "Issue Comment" — a conversation comment on the issue/PR, as opposed to an inline
 * review comment on the diff).
 */
@Data
public class GitHubIssueCommentDTO {
    public long id;
    public String url;
    public String body;
    public String htmlUrl;
    public String authorLogin;
    public String createdAt;
    public String updatedAt;
    public String authorAssociation;
}
