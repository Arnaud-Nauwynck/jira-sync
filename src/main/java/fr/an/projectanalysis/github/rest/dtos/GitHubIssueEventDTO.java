package fr.an.projectanalysis.github.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Flattened copy of {@code fr.an.projectanalysis.github.client.dtos.SourceGitHubIssueEventDTO}
 * (a GitHub "Issue Event" — labeled, assigned, milestoned, renamed, review_requested, locked,
 * ...). "Reference" objects (actor, assignee, assigner, ...) are collapsed to their plain
 * login/name, instead of being kept as nested objects. Fields are left null when not applicable
 * to the actual {@link #event} value.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitHubIssueEventDTO {
    public long id;
    public String url;
    public String actorLogin;
    /** Discriminator, e.g. "labeled", "assigned", "milestoned", "renamed", "review_requested", "locked", ... */
    public String event;
    public String commitId;
    public String commitUrl;
    public String createdAt;

    /** Set on "labeled" / "unlabeled" events. */
    public String labelName;
    public String labelColor;

    /** Set on "assigned" / "unassigned" events. */
    public String assigneeLogin;
    public String assignerLogin;

    /** Set on "milestoned" / "demilestoned" events. */
    public String milestoneTitle;

    /** Set on "renamed" events. */
    public String renameFrom;
    public String renameTo;

    /** Set on "review_requested" / "review_request_removed" events. */
    public String reviewRequesterLogin;
    public String requestedTeamName;
    public String requestedReviewerLogin;

    /** Set on "review_dismissed" events. */
    public String dismissedReviewState;
    public String dismissedReviewDismissalMessage;

    /** Set on "locked" events. */
    public String lockReason;

    /** Set on "issue_type_added" / "issue_type_removed" / "issue_type_changed" events. */
    public String issueTypeName;
    public String prevIssueTypeName;

    /** Set on "sub_issue_added" / "sub_issue_removed" events. */
    public Integer subIssueNumber;
    /** Set on "parent_issue_added" / "parent_issue_removed" events. */
    public Integer parentIssueNumber;
    /** Set on "blocked_by_added" / "blocked_by_removed" events. */
    public Integer blockedByNumber;
    /** Set on "blocking_added" / "blocking_removed" events. */
    public Integer blockingNumber;
}
