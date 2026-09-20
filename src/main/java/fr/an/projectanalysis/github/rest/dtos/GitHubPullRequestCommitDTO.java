package fr.an.projectanalysis.github.rest.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class GitHubPullRequestCommitDTO {
    public String sha;

    public String author;
    public String committer;
    public String message;

    @JsonProperty("comment_count")
    public Integer commentCount;

    public String parentSha0;
    public List<String> parentShaOthers;

    /** Only present on the single-commit detail response. */
    // public SourceGitHubCommitStatsDTO stats;
    public Integer additions;
    public Integer deletions;
    public Integer total;

    /** Only present on the single-commit detail response. */
    public List<GitHubCommitFileDTO> files;

    /** A single changed file within the commit ("Diff Entry"). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class GitHubCommitFileDTO {
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
