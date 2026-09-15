package fr.an.projectanalysis.github.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Mirrors one element of the JSON array returned by the GitHub REST API "issue events"
 * endpoints: GET /repos/{owner}/{repo}/issues/{issue_number}/events and
 * GET /repos/{owner}/{repo}/issues/events. GitHub returns a polymorphic union of ~25 event
 * subtypes (labeled, assigned, milestoned, renamed, review_requested, locked, ...), discriminated
 * by the {@code event} field; rather than modeling each subtype as a separate class, all their
 * fields are flattened onto this single class and left null when not applicable to the actual
 * {@link #event} value. GitHub's JSON is snake_case; fields below are explicitly annotated with
 * their source JSON name where it differs from the camelCase Java field name.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class SourceGitHubIssueEventDTO {
    public long id;
    @JsonProperty("node_id")
    public String nodeId;
    public String url;
    public SourceGitHubUserDTO actor;
    /** Discriminator, e.g. "labeled", "assigned", "milestoned", "renamed", "review_requested", "locked", ... */
    public String event;
    @JsonProperty("commit_id")
    public String commitId;
    @JsonProperty("commit_url")
    public String commitUrl;
    @JsonProperty("created_at")
    public String createdAt;

    /** Set on "labeled" / "unlabeled" events. */
    public SourceGitHubLabelDTO label;

    /** Set on "assigned" / "unassigned" events. */
    public SourceGitHubUserDTO assignee;
    public SourceGitHubUserDTO assigner;

    /** Set on "milestoned" / "demilestoned" events. */
    public SourceGitHubMilestoneRefDTO milestone;

    /** Set on "renamed" events. */
    public SourceGitHubRenameDTO rename;

    /** Set on "review_requested" / "review_request_removed" events. */
    @JsonProperty("review_requester")
    public SourceGitHubUserDTO reviewRequester;
    @JsonProperty("requested_team")
    public SourceGitHubTeamRefDTO requestedTeam;
    @JsonProperty("requested_reviewer")
    public SourceGitHubUserDTO requestedReviewer;

    /** Set on "review_dismissed" events. */
    @JsonProperty("dismissed_review")
    public SourceGitHubDismissedReviewDTO dismissedReview;

    /** Set on "locked" events. */
    @JsonProperty("lock_reason")
    public String lockReason;

    /** Set on "issue_type_added" / "issue_type_removed" / "issue_type_changed" events. */
    @JsonProperty("issue_type")
    public SourceGitHubIssueTypeDTO issueType;
    @JsonProperty("prev_issue_type")
    public SourceGitHubIssueTypeDTO prevIssueType;

    /** Set on "sub_issue_added" / "sub_issue_removed" events. */
    @JsonProperty("sub_issue")
    public SourceGitHubIssueRefDTO subIssue;
    /** Set on "parent_issue_added" / "parent_issue_removed" events. */
    @JsonProperty("parent_issue")
    public SourceGitHubIssueRefDTO parentIssue;
    /** Set on "blocked_by_added" / "blocked_by_removed" events. */
    @JsonProperty("blocked_by")
    public SourceGitHubIssueRefDTO blockedBy;
    /** Set on "blocking_added" / "blocking_removed" events. */
    public SourceGitHubIssueRefDTO blocking;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceGitHubUserDTO {
        public String login;
        public long id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubLabelDTO {
        public String name;
        public String color;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubMilestoneRefDTO {
        public String title;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubRenameDTO {
        public String from;
        public String to;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubTeamRefDTO {
        public String name;
        public String slug;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubDismissedReviewDTO {
        public String state;
        @JsonProperty("review_id")
        public Long reviewId;
        @JsonProperty("dismissal_message")
        public String dismissalMessage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubIssueTypeDTO {
        public String name;
        public String color;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubRepoRefDTO {
        @JsonProperty("full_name")
        public String fullName;
    }

    /** A minimal reference to another issue, e.g. a sub-issue, parent-issue, or dependency. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubIssueRefDTO {
        public int number;
        public String title;
        public String state;
        public SourceGitHubRepoRefDTO repository;
    }
}
