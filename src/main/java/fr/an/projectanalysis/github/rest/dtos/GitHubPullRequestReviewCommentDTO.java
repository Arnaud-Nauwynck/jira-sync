package fr.an.projectanalysis.github.rest.dtos;

import lombok.Data;

/**
 * Flattened copy of {@code fr.an.projectanalysis.github.client.dtos.SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO}
 * (a GitHub "Pull Request Review Comment" — an inline comment on a portion of the PR's diff).
 */
@Data
public class GitHubPullRequestReviewCommentDTO {
    public long id;
    public Long pullRequestReviewId;
    public String diffHunk;
    public String path;
    public Integer position;
    public Integer originalPosition;
    public String commitId;
    public String originalCommitId;
    public Long inReplyToId;
    public String authorLogin;
    public String body;
    public String createdAt;
    public String updatedAt;

    /** TODO remove.... useless, redundant with "https://github.com/{orga}/{repo}/pull/{number}#discussion_r{id}" */
    public String htmlUrl;

    public String authorAssociation;
    public Integer line;
    public Integer originalLine;
    public String side;
    public Integer startLine;
    public Integer originalStartLine;
    public String startSide;
    public String subjectType;
}
