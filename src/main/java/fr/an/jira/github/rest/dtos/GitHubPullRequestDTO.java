package fr.an.jira.github.rest.dtos;

import lombok.Data;

import java.util.List;

/**
 * Flattened / simplified copy of the {@code fr.an.jira.github.client.dtos.SourceGitHubPullRequestDTO}
 * class hierarchy: "reference" objects (user, assignees, requested reviewers, labels, milestone,
 * merged-by) are collapsed to their plain login/name, instead of being kept as nested objects.
 * See {@code fr.an.jira.github.mapper.SourceGitHubToAnnotatedPullRequestMapper} for the conversion
 * from {@code SourceGitHubPullRequestDTO}.
 */
@Data
public class GitHubPullRequestDTO {
    public long id;
    public int number;
    public String state;
    public String title;
    public String body;
    public String authorLogin;
    public String createdAt;
    public String updatedAt;
    public String closedAt;
    public String mergedAt;
    public boolean draft;
    public boolean merged;
    public List<String> assigneeLogins;
    public List<String> requestedReviewerLogins;
    public List<String> labelNames;
    public String milestoneTitle;
    public String headRef;
    public String headSha;
    public String baseRef;
    public String baseSha;
    public Boolean mergeable;
    public String mergeableState;
    public String mergedByLogin;
    public Integer comments;
    public Integer reviewComments;
    public Integer commits;
    public Integer additions;
    public Integer deletions;
    public Integer changedFiles;
    public String htmlUrl;
}
