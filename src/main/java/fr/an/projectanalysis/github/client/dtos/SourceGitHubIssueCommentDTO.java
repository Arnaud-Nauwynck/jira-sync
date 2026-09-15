package fr.an.projectanalysis.github.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Mirrors one element of the JSON array returned by the GitHub REST API "issue comments"
 * endpoints: GET /repos/{owner}/{repo}/issues/{issue_number}/comments and
 * GET /repos/{owner}/{repo}/issues/comments. GitHub's JSON is snake_case; fields below are
 * explicitly annotated with their source JSON name where it differs from the camelCase Java
 * field name.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceGitHubIssueCommentDTO {
    public long id;
    @JsonProperty("node_id")
    public String nodeId;
    public String url;
    public String body;
    @JsonProperty("html_url")
    public String htmlUrl;
    public SourceGitHubUserDTO user;
    @JsonProperty("created_at")
    public String createdAt;
    @JsonProperty("updated_at")
    public String updatedAt;
    @JsonProperty("issue_url")
    public String issueUrl;
    @JsonProperty("author_association")
    public String authorAssociation;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceGitHubUserDTO {
        public String login;
        public long id;
    }
}
