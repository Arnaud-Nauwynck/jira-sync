package fr.an.projectanalysis.github.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Mirrors one element of the JSON array returned by the GitHub REST API "commits" endpoints:
 * GET /repos/{owner}/{repo}/pulls/{pull_number}/commits (list, no {@link #stats}/{@link #files})
 * and GET /repos/{owner}/{repo}/commits/{ref} (single commit detail, with stats/files). GitHub's
 * JSON is snake_case; fields below are explicitly annotated with their source JSON name where it
 * differs from the camelCase Java field name.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class SourceGitHubPullRequestCommitDTO {
    public String sha;

    // TODO useless, remove
    @JsonProperty("html_url")
    public String htmlUrl;

    public SourceGitHubCommitDetailDTO commit;
    public SourceGitHubUserDTO author;
    public SourceGitHubUserDTO committer;
    public List<SourceGitHubCommitParentDTO> parents;

    /** Only present on the single-commit detail response. */
    public SourceGitHubCommitStatsDTO stats;
    /** Only present on the single-commit detail response. */
    public List<SourceGitHubCommitFileDTO> files;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubUserDTO {
        public String login;
        public long id;
    }

    /** The nested "commit" object: the raw Git commit data, as opposed to the GitHub-level wrapper. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubCommitDetailDTO {
        public SourceGitHubGitUserDTO author;
        public SourceGitHubGitUserDTO committer;
        public String message;
        @JsonProperty("comment_count")
        public Integer commentCount;
    }

    /** Git author/committer metadata (name/email/date), as opposed to a GitHub user account. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubGitUserDTO {
        public String name;
        public String email;
        public String date;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubCommitParentDTO {
        public String sha;

        // TODO useless, remove
        @JsonProperty("html_url")
        public String htmlUrl;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubCommitStatsDTO {
        public Integer additions;
        public Integer deletions;
        public Integer total;
    }

    /** A single changed file within the commit ("Diff Entry"). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class SourceGitHubCommitFileDTO {
        public String filename;
        /** One of "added", "removed", "modified", "renamed", "copied", "changed", "unchanged". */
        public String status;
        public Integer additions;
        public Integer deletions;
        public Integer changes;
        public String patch;
        @JsonProperty("previous_filename")
        public String previousFilename;
    }
}
