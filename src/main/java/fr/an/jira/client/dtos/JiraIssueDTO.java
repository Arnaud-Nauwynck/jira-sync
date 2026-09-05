package fr.an.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the JSON returned by the Jira REST API "issue" endpoint, e.g.
 * see src/test/data/issues/SPARK-12216.json.
 * Fields not explicitly declared (mostly the per-project "customfield_XXXXX" fields) are
 * collected in {@link JiraFieldsDTO#customFields}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class JiraIssueDTO {
    public String expand;
    public String id;
    public String self;
    public String key;
    public JiraFieldsDTO fields;
    public JiraChangelogDTO changelog;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraFieldsDTO {
        public List<Object> fixVersions;
        public JiraResolutionDTO resolution;
        public String lastViewed;
        public JiraPriorityDTO priority;
        public List<String> labels;
        public Long aggregatetimeoriginalestimate;
        public Long timeestimate;
        public List<Object> versions;
        public List<JiraIssueLinkDTO> issuelinks;
        public JiraUserDTO assignee;
        public List<Object> subtasks;
        public JiraStatusDTO status;
        public List<JiraComponentDTO> components;
        public String archiveddate;
        public Long aggregatetimeestimate;
        public JiraUserDTO creator;
        public JiraUserDTO reporter;
        public JiraProgressDTO aggregateprogress;
        public JiraProgressDTO progress;
        public JiraVotesDTO votes;
        public JiraWorklogDTO worklog;
        public Object archivedby;
        public JiraIssueTypeDTO issuetype;
        public Long timespent;
        public JiraProjectDTO project;
        public Long aggregatetimespent;
        public String resolutiondate;
        public Integer workratio;
        public JiraWatchesDTO watches;
        public String created;
        public String updated;
        public Long timeoriginalestimate;
        public String description;
        public String summary;
        public String environment;
        public String duedate;
        public JiraCommentsDTO comment;
        public Map<String, Object> timetracking;

        /** Catch-all for the per-project "customfield_XXXXX" fields (and any other unmapped field). */
        @JsonAnySetter
        public Map<String, Object> customFields = new LinkedHashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraUserDTO {
        public String self;
        public String name;
        public String key;
        public Map<String, String> avatarUrls;
        public String displayName;
        public boolean active;
        public String timeZone;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraResolutionDTO {
        public String self;
        public String id;
        public String description;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraPriorityDTO {
        public String self;
        public String iconUrl;
        public String name;
        public String id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraStatusCategoryDTO {
        public String self;
        public int id;
        public String key;
        public String colorName;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraStatusDTO {
        public String self;
        public String description;
        public String iconUrl;
        public String name;
        public String id;
        public JiraStatusCategoryDTO statusCategory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraComponentDTO {
        public String self;
        public String id;
        public String name;
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraIssueTypeDTO {
        public String self;
        public String id;
        public String description;
        public String iconUrl;
        public String name;
        public boolean subtask;
        public Integer avatarId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraIssueLinkTypeDTO {
        public String id;
        public String self;
        public String name;
        public String inward;
        public String outward;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraLinkedIssueFieldsDTO {
        public String summary;
        public JiraStatusDTO status;
        public JiraPriorityDTO priority;
        public JiraIssueTypeDTO issuetype;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraLinkedIssueDTO {
        public String id;
        public String key;
        public String self;
        public JiraLinkedIssueFieldsDTO fields;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraIssueLinkDTO {
        public String id;
        public String self;
        public JiraIssueLinkTypeDTO type;
        public JiraLinkedIssueDTO inwardIssue;
        public JiraLinkedIssueDTO outwardIssue;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraProjectDTO {
        public String self;
        public String id;
        public String key;
        public String name;
        public String projectTypeKey;
        public Map<String, String> avatarUrls;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraProgressDTO {
        public int progress;
        public int total;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraVotesDTO {
        public String self;
        public int votes;
        public boolean hasVoted;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraWorklogDTO {
        public int startAt;
        public int maxResults;
        public int total;
        public List<Object> worklogs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraWatchesDTO {
        public String self;
        public int watchCount;
        public boolean isWatching;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraCommentDTO {
        public String self;
        public String id;
        public JiraUserDTO author;
        public String body;
        public JiraUserDTO updateAuthor;
        public String created;
        public String updated;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraCommentsDTO {
        public List<JiraCommentDTO> comments;
        public int maxResults;
        public int total;
        public int startAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraChangelogDTO {
        public int startAt;
        public int maxResults;
        public int total;
        public List<JiraHistoryDTO> histories;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraHistoryDTO {
        public String id;
        public JiraUserDTO author;
        public String created;
        public List<JiraHistoryItemDTO> items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class JiraHistoryItemDTO {
        public String field;
        public String fieldtype;
        public String from;
        public String fromString;
        public String to;
        public String toString;
    }
}
