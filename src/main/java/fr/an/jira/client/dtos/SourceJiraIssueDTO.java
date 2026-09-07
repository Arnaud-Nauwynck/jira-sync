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
 * collected in {@link SourceJiraFieldsDTO#customFields}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraIssueDTO {
    public String expand;
    public String id;
    public String self;
    public String key;
    public SourceJiraFieldsDTO fields;
    public SourceJiraChangelogDTO changelog;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraFieldsDTO {
        public List<Object> fixVersions;
        public SourceJiraResolutionDTO resolution;
        public String lastViewed;
        public SourceJiraPriorityDTO priority;
        public List<String> labels;
        public Long aggregatetimeoriginalestimate;
        public Long timeestimate;
        public List<SourceJiraVersionDTO> versions;
        public List<SourceJiraIssueLinkDTO> issuelinks;
        public SourceJiraUserDTO assignee;
        public List<Object> subtasks;
        public SourceJiraStatusDTO status;
        public List<SourceJiraComponentDTO> components;
        public String archiveddate;
        public Long aggregatetimeestimate;
        public SourceJiraUserDTO creator;
        public SourceJiraUserDTO reporter;
        public SourceJiraProgressDTO aggregateprogress;
        public SourceJiraProgressDTO progress;
        public SourceJiraVotesDTO votes;
        public JiraWorklogDTO worklog;
        public Object archivedby;
        public SourceJiraIssueTypeDTO issuetype;
        public Long timespent;
        public SourceJiraProjectDTO project;
        public Long aggregatetimespent;
        public String resolutiondate;
        public Integer workratio;
        public SourceJiraWatchesDTO watches;
        public String created;
        public String updated;
        public Long timeoriginalestimate;
        public String description;
        public String summary;
        public String environment;
        public String duedate;
        public SourceJiraCommentsDTO comment;
        public Map<String, Object> timetracking;

        /** Catch-all for the per-project "customfield_XXXXX" fields (and any other unmapped field). */
        @JsonAnySetter
        public Map<String, Object> customFields = new LinkedHashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraUserDTO {
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
    public static class SourceJiraResolutionDTO {
        public String self;
        public String id;
        public String description;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraPriorityDTO {
        public String self;
        public String iconUrl;
        public String name;
        public String id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraStatusCategoryDTO {
        public String self;
        public int id;
        public String key;
        public String colorName;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraStatusDTO {
        public String self;
        public String description;
        public String iconUrl;
        public String name;
        public String id;
        public SourceJiraStatusCategoryDTO statusCategory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraComponentDTO {
        public String self;
        public String id;
        public String name;
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraVersionDTO {
        public String self;
        public String id;
        public String description;
        public String name;
        public boolean archived;
        public boolean released;
        public String releaseDate;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraIssueTypeDTO {
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
    public static class SourceJiraIssueLinkTypeDTO {
        public String id;
        public String self;
        public String name;
        public String inward;
        public String outward;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraLinkedIssueFieldsDTO {
        public String summary;
        public SourceJiraStatusDTO status;
        public SourceJiraPriorityDTO priority;
        public SourceJiraIssueTypeDTO issuetype;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraLinkedIssueDTO {
        public String id;
        public String key;
        public String self;
        public SourceJiraLinkedIssueFieldsDTO fields;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraIssueLinkDTO {
        public String id;
        public String self;
        public SourceJiraIssueLinkTypeDTO type;
        public SourceJiraLinkedIssueDTO inwardIssue;
        public SourceJiraLinkedIssueDTO outwardIssue;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraProjectDTO {
        public String self;
        public String id;
        public String key;
        public String name;
        public String projectTypeKey;
        public Map<String, String> avatarUrls;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraProgressDTO {
        public int progress;
        public int total;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraVotesDTO {
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
    public static class SourceJiraWatchesDTO {
        public String self;
        public int watchCount;
        public boolean isWatching;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraCommentDTO {
        public String self;
        public String id;
        public SourceJiraUserDTO author;
        public String body;
        public SourceJiraUserDTO updateAuthor;
        public String created;
        public String updated;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraCommentsDTO {
        public List<SourceJiraCommentDTO> comments;
        public int maxResults;
        public int total;
        public int startAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraChangelogDTO {
        public int startAt;
        public int maxResults;
        public int total;
        public List<SourceJiraHistoryDTO> histories;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraHistoryDTO {
        public String id;
        public SourceJiraUserDTO author;
        public String created;
        public List<SourceJiraHistoryItemDTO> items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraHistoryItemDTO {
        public String field;
        public String fieldtype;
        public String from;
        public String fromString;
        public String to;
        public String toString;
    }
}
