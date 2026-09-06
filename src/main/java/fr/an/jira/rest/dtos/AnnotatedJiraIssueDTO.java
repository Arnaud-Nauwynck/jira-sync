package fr.an.jira.rest.dtos;

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
public class AnnotatedJiraIssueDTO {
    public String expand;
    public String id;
    public String self;
    public String key;
    public AnnotatedJiraFieldsDTO fields;
    public AnnotatedJiraChangelogDTO changelog;

    /** Extra data not coming from the source Jira server; not set by the mapper. */
    public JiraAnnotatedDTO annotated;

    @Data
    public static class AnnotatedJiraFieldsDTO {
        public List<Object> fixVersions;
        public String resolution;
        public String lastViewed;
        public String priority;
        public List<String> labels;
        public Long aggregatetimeoriginalestimate;
        public Long timeestimate;
        public List<Object> versions;
        public List<AnnotatedJiraIssueLinkDTO> issuelinks;
        public String assignee;
        public List<Object> subtasks;
        public String status;
        public List<String> components;
        public String archiveddate;
        public Long aggregatetimeestimate;
        public String creator;
        public String reporter;
        public AnnotatedJiraProgressDTO aggregateprogress;
        public AnnotatedJiraProgressDTO progress;
        public AnnotatedJiraVotesDTO votes;
        public AnnotatedJiraWorklogDTO worklog;
        public Object archivedby;
        public String issuetype;
        public Long timespent;
        public String project;
        public Long aggregatetimespent;
        public String resolutiondate;
        public Integer workratio;
        public AnnotatedJiraWatchesDTO watches;
        public String created;
        public String updated;
        public Long timeoriginalestimate;
        public String description;
        public String summary;
        public String environment;
        public String duedate;
        public AnnotatedJiraCommentsDTO comment;
        public Map<String, Object> timetracking;
        public Map<String, Object> customFields = new LinkedHashMap<>();
    }

    @Data
    public static class AnnotatedJiraIssueLinkDTO {
        public String id;
        public String self;
        public AnnotatedJiraIssueLinkTypeDTO type;
        public AnnotatedJiraLinkedIssueDTO inwardIssue;
        public AnnotatedJiraLinkedIssueDTO outwardIssue;
    }

    @Data
    public static class AnnotatedJiraIssueLinkTypeDTO {
        public String id;
        public String self;
        public String name;
        public String inward;
        public String outward;
    }

    @Data
    public static class AnnotatedJiraLinkedIssueDTO {
        public String id;
        public String key;
        public String self;
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
    public static class AnnotatedJiraProgressDTO {
        public int progress;
        public int total;
    }

    @Data
    public static class AnnotatedJiraVotesDTO {
        public String self;
        public int votes;
        public boolean hasVoted;
    }

    @Data
    public static class AnnotatedJiraWorklogDTO {
        public int startAt;
        public int maxResults;
        public int total;
        public List<Object> worklogs;
    }

    @Data
    public static class AnnotatedJiraWatchesDTO {
        public String self;
        public int watchCount;
        public boolean isWatching;
    }

    @Data
    public static class AnnotatedJiraCommentDTO {
        public String self;
        public String id;
        public String author;
        public String body;
        public String updateAuthor;
        public String created;
        public String updated;
    }

    @Data
    public static class AnnotatedJiraCommentsDTO {
        public List<AnnotatedJiraCommentDTO> comments;
        public int maxResults;
        public int total;
        public int startAt;
    }

    @Data
    public static class AnnotatedJiraChangelogDTO {
        public int startAt;
        public int maxResults;
        public int total;
        public List<AnnotatedJiraHistoryDTO> histories;
    }

    @Data
    public static class AnnotatedJiraHistoryDTO {
        public String id;
        public String author;
        public String created;
        public List<AnnotatedJiraHistoryItemDTO> items;
    }

    @Data
    public static class AnnotatedJiraHistoryItemDTO {
        public String field;
        public String fieldtype;
        public String from;
        public String fromString;
        public String to;
        public String toString;
    }

    /** Extra fields enriched and persisted locally, not coming from the source Jira server. */
    @Data
    public static class JiraAnnotatedDTO {
        public String comment;
        public LocalDateTime commentTime;
        public String summarised;
        public LocalDateTime summarisedTime;
        public String interrest;
    }
}
