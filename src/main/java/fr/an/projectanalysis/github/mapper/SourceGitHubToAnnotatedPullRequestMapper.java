package fr.an.projectanalysis.github.mapper;

import fr.an.projectanalysis.github.client.dtos.SourceGitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts the raw {@link SourceGitHubPullRequestDTO} class hierarchy (mirroring the GitHub REST
 * API JSON) into the flattened {@link GitHubPullRequestDTO} hierarchy.
 */
public class SourceGitHubToAnnotatedPullRequestMapper {

    private SourceGitHubToAnnotatedPullRequestMapper() {
    }

    public static GitHubPullRequestDTO from(SourceGitHubPullRequestDTO src) {
        if (src == null) {
            return null;
        }
        GitHubPullRequestDTO dest = new GitHubPullRequestDTO();
        dest.id = src.id;
        dest.number = src.number;
        dest.state = src.state;
        dest.title = src.title;
        dest.body = src.body;
        dest.authorLogin = login(src.user);
        dest.createdAt = src.createdAt;
        dest.updatedAt = src.updatedAt;
        dest.closedAt = src.closedAt;
        dest.mergedAt = src.mergedAt;
        dest.draft = src.draft;
        dest.merged = src.merged != null && src.merged;
        dest.assigneeLogins = logins(src.assignees);
        dest.requestedReviewerLogins = logins(src.requestedReviewers);
        dest.labelNames = src.labels == null ? null
                : src.labels.stream().map(l -> l.name).collect(Collectors.toList());
        dest.milestoneTitle = src.milestone != null ? src.milestone.title : null;
        dest.headRef = src.head != null ? src.head.ref : null;
        dest.headSha = src.head != null ? src.head.sha : null;
        dest.baseRef = src.base != null ? src.base.ref : null;
        dest.baseSha = src.base != null ? src.base.sha : null;
        dest.mergeable = src.mergeable;
        dest.mergeableState = src.mergeableState;
        dest.mergedByLogin = login(src.mergedBy);
        dest.comments = src.comments;
        dest.reviewComments = src.reviewComments;
        dest.commits = src.commits;
        dest.additions = src.additions;
        dest.deletions = src.deletions;
        dest.changedFiles = src.changedFiles;
        dest.htmlUrl = src.htmlUrl;
        return dest;
    }

    private static String login(SourceGitHubPullRequestDTO.SourceGitHubUserDTO user) {
        return user != null ? user.login : null;
    }

    private static List<String> logins(List<SourceGitHubPullRequestDTO.SourceGitHubUserDTO> users) {
        return users == null ? null
                : users.stream().map(SourceGitHubToAnnotatedPullRequestMapper::login).collect(Collectors.toList());
    }
}
