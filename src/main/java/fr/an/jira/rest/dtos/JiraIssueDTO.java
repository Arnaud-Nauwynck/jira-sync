package fr.an.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattened / simplified copy of the {@code fr.an.jira.client.dtos.JiraIssueDTO} class hierarchy:
 * - small "reference" objects (resolution, priority, status, statusCategory, user, project,
 *   issuetype, component) are collapsed to their plain {@code name} (the user is a special case,
 *   see {@code JiraToAnnotatedIssueMapper.userDisplay}), instead of being kept as nested
 *   self/id/name objects.
 * - an {@link #annotated} section is added to carry extra data that does not come from the
 *   source Jira server, and is not filled in by the mapping from {@code JiraIssueDTO}.
 * See {@code fr.an.jira.mapper.JiraToAnnotatedIssueMapper} for the conversion from
 * {@code JiraIssueDTO}.
 */
@Data
public class JiraIssueDTO {
    public String id;
    public String key;
    public IssueFieldsDTO fields;
    public List<IssueHistoryDTO> histories;

    /** Extra data not coming from the source Jira server; not set by the mapper. */
    public IssueExtraFieldsDTO annotated;

    @Data
    public static class IssueFieldsDTO {
        public List<Object> fixVersions;
        public String resolution;
        public String lastViewed;
        public String priority;
        public List<String> labels;
        public Long aggregatetimeoriginalestimate;
        public Long timeestimate;
        public List<Object> versions;
        public List<IssueLinkDTO> issuelinks;
        public String assignee;
        public List<Object> subtasks;
        public String status;
        public List<String> components;
        public String archiveddate;
        public Long aggregatetimeestimate;
        public String creator;
        public String reporter;
        public IssueProgressDTO aggregateprogress;
        public IssueProgressDTO progress;
        public int votes;
        public Boolean hasVoted;
        public List<Object> worklogs;
        public Object archivedby;
        public String issuetype;
        public Long timespent;
        public String project;
        public Long aggregatetimespent;
        public String resolutiondate;
        public Integer workratio;
        @JsonProperty("watch#")
        public int watchCount;
        public Boolean watching;
        public String created;
        public String updated;
        public Long timeoriginalestimate;
        public String description;
        public String summary;
        public String environment;
        public String duedate;
        public List<IssueCommentDTO> comments;
        public Map<String, Object> timetracking;
        public Map<String, Object> customFields = new LinkedHashMap<>();
    }

    @Data
    public static class IssueLinkDTO {
        public String id;
        public IssueLinkTypeDTO type;
        public LinkedIssueDTO inwardIssue;
        public LinkedIssueDTO outwardIssue;
    }

    @Data
    public static class IssueLinkTypeDTO {
        public String id;
        public String name;
        public String inward;
        public String outward;
    }

    @Data
    public static class LinkedIssueDTO {
        public String id;
        public String key;
        public AnnotatedJiraLinkedIssueFieldsDTO fields;
    }

    @Data
    public static class AnnotatedJiraLinkedIssueFieldsDTO {
        public String summary;
        public String status;
        public String priority;
        public String issuetype;
    }

    @Data
    public static class IssueProgressDTO {
        public int progress;
        public int total;
    }

    @Data
    public static class IssueCommentDTO {
        public String id;
        public String author;
        public String body;
        public String updateAuthor;
        public String created;
        public String updated;
    }

    @Data
    public static class IssueHistoryDTO {
        public String id;
        public String author;
        public String created;
        public List<IssueHistoryItemDTO> items;
    }

    @Data
    public static class IssueHistoryItemDTO {
        public String field;
        public String fieldtype;
        public String from;
        public String fromString;
        public String to;
        public String toString;
    }

    /** Extra fields enriched and persisted locally, not coming from the source Jira server. */
    @Data
    public static class IssueExtraFieldsDTO {
        public String comment;
        public LocalDateTime commentTime;
        public String summarised;
        public LocalDateTime summarisedTime;
        public String interrest;
    }
}
