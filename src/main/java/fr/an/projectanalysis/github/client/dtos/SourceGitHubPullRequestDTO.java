package fr.an.projectanalysis.github.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Mirrors the JSON returned by the GitHub REST API "pulls" endpoints:
 * GET /repos/{owner}/{repo}/pulls (list) and GET /repos/{owner}/{repo}/pulls/{number} (detail).
 * The detail-only fields (mergeable, mergeable_state, merged_by, comments, review_comments,
 * commits, additions, deletions, changed_files) are left null when only the list response was
 * used to populate this DTO. GitHub's JSON is snake_case; fields below are explicitly annotated
 * with their source JSON name where it differs from the camelCase Java field name.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceGitHubPullRequestDTO {
    public long id;
    @JsonProperty("node_id")
    public String nodeId;
    public int number;
    public String state;
    public boolean locked;
    public String title;
    public String body;
    public SourceGitHubUserDTO user;
    @JsonProperty("created_at")
    public String createdAt;
    @JsonProperty("updated_at")
    public String updatedAt;
    @JsonProperty("closed_at")
    public String closedAt;
    @JsonProperty("merged_at")
    public String mergedAt;
    @JsonProperty("merge_commit_sha")
    public String mergeCommitSha;
    public boolean draft;
    public Boolean merged;
    public SourceGitHubUserDTO assignee;
    public List<SourceGitHubUserDTO> assignees;
    @JsonProperty("requested_reviewers")
    public List<SourceGitHubUserDTO> requestedReviewers;
    public List<SourceGitHubLabelDTO> labels;
    public SourceGitHubMilestoneDTO milestone;
    public SourceGitHubBranchDTO head;
    public SourceGitHubBranchDTO base;
    @JsonProperty("author_association")
    public String authorAssociation;

    @JsonProperty("html_url")
    public String htmlUrl;

    /** Only present on the single-PR detail response. */
    public Boolean mergeable;
    @JsonProperty("mergeable_state")
    public String mergeableState;
    @JsonProperty("merged_by")
    public SourceGitHubUserDTO mergedBy;
    public Integer comments;
    @JsonProperty("review_comments")
    public Integer reviewComments;
    @JsonProperty("maintainer_can_modify")
    public Boolean maintainerCanModify;
    public Integer commits;
    public Integer additions;
    public Integer deletions;
    @JsonProperty("changed_files")
    public Integer changedFiles;

    /** when count 'commits' is set, populated separately by GitHubPullRequestSyncRunner via GET .../issues/{number}/commits, not part of the detail response. */
    public List<SourceGitHubPullRequestCommitDTO> commitsData;

    /** when count 'comments' is set, populated separately by GitHubPullRequestSyncRunner via GET .../issues/{number}/comments, not part of the detail response. */
    public List<SourceGitHubIssueCommentDTO> commentsData;

    /** when count 'reviewComments' is set, populated separately by GitHubPullRequestSyncRunner via GET .../pulls/{number}/comments, not part of the detail response. */
    public List<SourceGitHubReviewCommentDTO> reviewCommentsData;

    /** populated separately by GitHubPullRequestSyncRunner via GET .../issues/{number}/events, not part of the detail response. */
    public List<SourceGitHubIssueEventDTO> issueEventsData;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubUserDTO {
        public String login;
        public long id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubReviewCommentDTO {
        public long id;
        @JsonProperty("pull_request_review_id")
        public Long pullRequestReviewId;
        @JsonProperty("diff_hunk")
        public String diffHunk;
        public String path;
        public Integer position;
        @JsonProperty("original_position")
        public Integer originalPosition;
        @JsonProperty("commit_id")
        public String commitId;
        @JsonProperty("original_commit_id")
        public String originalCommitId;
        @JsonProperty("in_reply_to_id")
        public Long inReplyToId;
        public SourceGitHubUserDTO user;
        public String body;
        @JsonProperty("created_at")
        public String createdAt;
        @JsonProperty("updated_at")
        public String updatedAt;
        @JsonProperty("html_url")
        public String htmlUrl;
        @JsonProperty("author_association")
        public String authorAssociation;
        public Integer line;
        @JsonProperty("original_line")
        public Integer originalLine;
        public String side;
        @JsonProperty("start_line")
        public Integer startLine;
        @JsonProperty("original_start_line")
        public Integer originalStartLine;
        @JsonProperty("start_side")
        public String startSide;
        @JsonProperty("subject_type")
        public String subjectType;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubLabelDTO {
        public long id;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubMilestoneDTO {
        public int number;
        public String title;
        public String state;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubRepoRefDTO {
        @JsonProperty("full_name")
        public String fullName;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubBranchDTO {
        public String label;
        public String ref;
        public String sha;
        public SourceGitHubUserDTO user;
        public SourceGitHubRepoRefDTO repo;
    }
}
