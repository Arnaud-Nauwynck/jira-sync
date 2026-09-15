package fr.an.projectanalysis.github.mapper;

import fr.an.projectanalysis.github.client.dtos.SourceGitHubCommitDTO;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubIssueCommentDTO;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubIssueEventDTO;
import fr.an.projectanalysis.github.client.dtos.SourceGitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubIssueCommentDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubIssueEventDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestReviewCommentDTO;

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
        dest.reviewCommentsData = mapReviewComments(src.reviewCommentsData);
        dest.commentsData = mapComments(src.commentsData);
        dest.issueEventsData = mapIssueEvents(src.issueEventsData);
        return dest;
    }

    /** Maps the raw review-comment list to its flattened form; also used to backfill PRs missing this data. */
    public static List<GitHubPullRequestReviewCommentDTO> mapReviewComments(List<SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO> src) {
        return src == null ? null
                : src.stream()
                        .map(SourceGitHubToAnnotatedPullRequestMapper::reviewComment)
                        .collect(Collectors.toList());
    }

    /** Maps the raw issue-comment list to its flattened form; also used to backfill PRs missing this data. */
    public static List<GitHubIssueCommentDTO> mapComments(List<SourceGitHubIssueCommentDTO> src) {
        return src == null ? null
                : src.stream()
                        .map(SourceGitHubToAnnotatedPullRequestMapper::comment)
                        .collect(Collectors.toList());
    }

    /** Maps the raw issue-event list to its flattened form; also used to backfill PRs missing this data. */
    public static List<GitHubIssueEventDTO> mapIssueEvents(List<SourceGitHubIssueEventDTO> src) {
        return src == null ? null
                : src.stream()
                        .map(SourceGitHubToAnnotatedPullRequestMapper::issueEvent)
                        .collect(Collectors.toList());
    }

    /**
     * Derives minimal commit references from issue-timeline events that carry a {@code commitId}
     * (e.g. "referenced", "closed", "merged", "head_ref_force_pushed" events). Note this only
     * captures the commit sha/htmlUrl embedded in the timeline event, not the full commit detail
     * (message, author, stats, files) — that requires the dedicated
     * GET /pulls/{number}/commits endpoint.
     */
    public static List<SourceGitHubCommitDTO> mapIssueCommits(List<SourceGitHubIssueEventDTO> src) {
        return src == null ? null
                : src.stream()
                        .filter(e -> e.commitId != null)
                        .map(SourceGitHubToAnnotatedPullRequestMapper::issueEventCommit)
                        .collect(Collectors.toList());
    }

    private static String login(SourceGitHubPullRequestDTO.SourceGitHubUserDTO user) {
        return user != null ? user.login : null;
    }

    private static String login(SourceGitHubIssueCommentDTO.SourceGitHubUserDTO user) {
        return user != null ? user.login : null;
    }

    private static String login(SourceGitHubIssueEventDTO.SourceGitHubUserDTO user) {
        return user != null ? user.login : null;
    }

    private static List<String> logins(List<SourceGitHubPullRequestDTO.SourceGitHubUserDTO> users) {
        return users == null ? null
                : users.stream().map(SourceGitHubToAnnotatedPullRequestMapper::login).collect(Collectors.toList());
    }

    private static GitHubPullRequestReviewCommentDTO reviewComment(SourceGitHubPullRequestDTO.SourceGitHubReviewCommentDTO c) {
        GitHubPullRequestReviewCommentDTO d = new GitHubPullRequestReviewCommentDTO();
        d.id = c.id;
        d.pullRequestReviewId = c.pullRequestReviewId;
        d.diffHunk = c.diffHunk;
        d.path = c.path;
        d.position = c.position;
        d.originalPosition = c.originalPosition;
        d.commitId = c.commitId;
        d.originalCommitId = c.originalCommitId;
        d.inReplyToId = c.inReplyToId;
        d.authorLogin = login(c.user);
        d.body = c.body;
        d.createdAt = c.createdAt;
        d.updatedAt = c.updatedAt;
        d.authorAssociation = c.authorAssociation;
        d.line = c.line;
        d.originalLine = c.originalLine;
        d.side = c.side;
        d.startLine = c.startLine;
        d.originalStartLine = c.originalStartLine;
        d.startSide = c.startSide;
        d.subjectType = c.subjectType;
        return d;
    }

    private static GitHubIssueCommentDTO comment(SourceGitHubIssueCommentDTO c) {
        GitHubIssueCommentDTO d = new GitHubIssueCommentDTO();
        d.id = c.id;
        d.url = c.url;
        d.body = c.body;
        d.htmlUrl = c.htmlUrl;
        d.authorLogin = login(c.user);
        d.createdAt = c.createdAt;
        d.updatedAt = c.updatedAt;
        d.authorAssociation = c.authorAssociation;
        return d;
    }

    private static GitHubIssueEventDTO issueEvent(SourceGitHubIssueEventDTO e) {
        GitHubIssueEventDTO d = new GitHubIssueEventDTO();
        d.id = e.id;
        d.url = e.url;
        d.actorLogin = login(e.actor);
        d.event = e.event;
        d.commitId = e.commitId;
        d.commitUrl = e.commitUrl;
        d.createdAt = e.createdAt;
        if (e.label != null) {
            d.labelName = e.label.name;
            d.labelColor = e.label.color;
        }
        d.assigneeLogin = login(e.assignee);
        d.assignerLogin = login(e.assigner);
        d.milestoneTitle = e.milestone != null ? e.milestone.title : null;
        if (e.rename != null) {
            d.renameFrom = e.rename.from;
            d.renameTo = e.rename.to;
        }
        d.reviewRequesterLogin = login(e.reviewRequester);
        d.requestedTeamName = e.requestedTeam != null ? e.requestedTeam.name : null;
        d.requestedReviewerLogin = login(e.requestedReviewer);
        if (e.dismissedReview != null) {
            d.dismissedReviewState = e.dismissedReview.state;
            d.dismissedReviewDismissalMessage = e.dismissedReview.dismissalMessage;
        }
        d.lockReason = e.lockReason;
        d.issueTypeName = e.issueType != null ? e.issueType.name : null;
        d.prevIssueTypeName = e.prevIssueType != null ? e.prevIssueType.name : null;
        d.subIssueNumber = e.subIssue != null ? e.subIssue.number : null;
        d.parentIssueNumber = e.parentIssue != null ? e.parentIssue.number : null;
        d.blockedByNumber = e.blockedBy != null ? e.blockedBy.number : null;
        d.blockingNumber = e.blocking != null ? e.blocking.number : null;
        return d;
    }

    private static SourceGitHubCommitDTO issueEventCommit(SourceGitHubIssueEventDTO e) {
        SourceGitHubCommitDTO d = new SourceGitHubCommitDTO();
        d.sha = e.commitId;
        d.htmlUrl = e.commitUrl;
        return d;
    }
}
