package fr.an.jira.mapper;

import fr.an.jira.client.dtos.SourceJiraIssueDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO.IssueCommentDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO.IssueFieldsDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO.IssueHistoryDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO.IssueHistoryItemDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO.IssueLinkDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO.IssueLinkTypeDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO.LinkedIssueDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO.AnnotatedJiraLinkedIssueFieldsDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO.IssueProgressDTO;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Converts the raw {@link SourceJiraIssueDTO} class hierarchy (mirroring the Jira REST API JSON)
 * into the flattened {@link JiraIssueDTO} hierarchy.
 * Does not set {@link JiraIssueDTO#annotated}, which carries data enriched/persisted
 * locally rather than coming from the source Jira server.
 */
public class SourceJiraToAnnotatedIssueMapper {

    private SourceJiraToAnnotatedIssueMapper() {
    }

    public static JiraIssueDTO from(SourceJiraIssueDTO src) {
        if (src == null) {
            return null;
        }
        JiraIssueDTO dest = new JiraIssueDTO();
        dest.id = src.id;
        dest.key = src.key;
        dest.fields = from(src.fields);
        dest.histories = src.changelog == null ? null
                : src.changelog.histories == null ? null
                : src.changelog.histories.stream().map(SourceJiraToAnnotatedIssueMapper::from).collect(Collectors.toList());
        return dest;
    }

    static IssueFieldsDTO from(SourceJiraIssueDTO.SourceJiraFieldsDTO src) {
        if (src == null) {
            return null;
        }
        IssueFieldsDTO dest = new IssueFieldsDTO();
        dest.fixVersions = src.fixVersions;
        dest.resolution = (src.resolution != null) ? src.resolution.name : null;
        dest.lastViewed = src.lastViewed;
        dest.priority = (src.priority != null) ? src.priority.name : null;
        dest.labels = src.labels;
        dest.aggregatetimeoriginalestimate = src.aggregatetimeoriginalestimate;
        dest.timeestimate = src.timeestimate;
        dest.versions = src.versions == null ? null
                : src.versions.stream().map(v -> v.name).collect(Collectors.toList());
        dest.issuelinks = src.issuelinks == null ? null
                : src.issuelinks.stream().map(SourceJiraToAnnotatedIssueMapper::from).collect(Collectors.toList());
        dest.assignee = userDisplay(src.assignee);
        dest.subtasks = src.subtasks;
        dest.status = (src.status != null) ? src.status.name : null;
        dest.components = src.components == null ? null
                : src.components.stream().map(c -> c.name).collect(Collectors.toList());
        dest.archiveddate = src.archiveddate;
        dest.aggregatetimeestimate = src.aggregatetimeestimate;
        dest.creator = userDisplay(src.creator);
        dest.reporter = userDisplay(src.reporter);
        dest.aggregateprogress = from(src.aggregateprogress);
        dest.progress = from(src.progress);
        dest.votes = (src.votes != null) ? src.votes.votes : 0;
        dest.hasVoted = src.votes != null && src.votes.hasVoted;
        dest.worklogs = src.worklog == null || src.worklog.worklogs == null? null
            : src.worklog.worklogs; // TODO .stream().map(SourceJiraToAnnotatedIssueMapper::fromWorklog).collect(Collectors.toList());
        dest.archivedby = src.archivedby;
        dest.issuetype = (src.issuetype != null) ? src.issuetype.name : null;
        dest.timespent = src.timespent;
        dest.project = (src.project != null) ? src.project.name : null;
        dest.aggregatetimespent = src.aggregatetimespent;
        dest.resolutiondate = src.resolutiondate;
        dest.workratio = src.workratio;
        dest.watchCount = (src.watches != null && src.watches.watchCount != 0) ? src.watches.watchCount : null;
        dest.watching = src.watches != null && src.watches.isWatching;
        dest.created = src.created;
        dest.updated = src.updated;
        dest.timeoriginalestimate = src.timeoriginalestimate;
        dest.description = src.description;
        dest.summary = src.summary;
        dest.environment = src.environment;
        dest.duedate = src.duedate;
        dest.comments = src.comment == null || src.comment.comments == null ? null
                : src.comment.comments.stream().map(SourceJiraToAnnotatedIssueMapper::from).collect(Collectors.toList());
        dest.timetracking = src.timetracking;
        dest.customFields = src.customFields;
        return dest;
    }

    static IssueLinkDTO from(SourceJiraIssueDTO.SourceJiraIssueLinkDTO src) {
        if (src == null) {
            return null;
        }
        IssueLinkDTO dest = new IssueLinkDTO();
        dest.id = src.id;
        dest.type = from(src.type);
        dest.inwardIssue = from(src.inwardIssue);
        dest.outwardIssue = from(src.outwardIssue);
        return dest;
    }

    static IssueLinkTypeDTO from(SourceJiraIssueDTO.SourceJiraIssueLinkTypeDTO src) {
        if (src == null) {
            return null;
        }
        IssueLinkTypeDTO dest = new IssueLinkTypeDTO();
        dest.id = src.id;
        dest.name = src.name;
        dest.inward = src.inward;
        dest.outward = src.outward;
        return dest;
    }

    static LinkedIssueDTO from(SourceJiraIssueDTO.SourceJiraLinkedIssueDTO src) {
        if (src == null) {
            return null;
        }
        LinkedIssueDTO dest = new LinkedIssueDTO();
        dest.id = src.id;
        dest.key = src.key;
        dest.fields = from(src.fields);
        return dest;
    }

    static AnnotatedJiraLinkedIssueFieldsDTO from(SourceJiraIssueDTO.SourceJiraLinkedIssueFieldsDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraLinkedIssueFieldsDTO dest = new AnnotatedJiraLinkedIssueFieldsDTO();
        dest.summary = src.summary;
        dest.status = (src.status != null) ? src.status.name : null;
        dest.priority = (src.priority != null) ? src.priority.name : null;
        dest.issuetype = (src.issuetype != null) ? src.issuetype.name : null;
        return dest;
    }

    static IssueProgressDTO from(SourceJiraIssueDTO.SourceJiraProgressDTO src) {
        if (src == null) {
            return null;
        }
        IssueProgressDTO dest = new IssueProgressDTO();
        dest.progress = src.progress;
        dest.total = src.total;
        return dest;
    }

    /**
     * {@code updateAuthor}/{@code updated} are left null when they are redundant with
     * {@code author}/{@code created}, i.e. the comment was never edited after being posted.
     */
    static IssueCommentDTO from(SourceJiraIssueDTO.SourceJiraCommentDTO src) {
        if (src == null) {
            return null;
        }
        IssueCommentDTO dest = new IssueCommentDTO();
        dest.id = src.id;
        dest.author = userDisplay(src.author);
        dest.body = src.body;
        dest.created = src.created;
        String updateAuthor = userDisplay(src.updateAuthor);
        dest.updateAuthor = Objects.equals(updateAuthor, dest.author) ? null : updateAuthor;
        dest.updated = Objects.equals(src.updated, src.created) ? null : src.updated;
        return dest;
    }

    static IssueHistoryDTO from(SourceJiraIssueDTO.SourceJiraHistoryDTO src) {
        if (src == null) {
            return null;
        }
        IssueHistoryDTO dest = new IssueHistoryDTO();
        dest.id = src.id;
        dest.author = userDisplay(src.author);
        dest.created = src.created;
        dest.items = src.items == null ? null
                : src.items.stream().map(SourceJiraToAnnotatedIssueMapper::from).collect(Collectors.toList());
        return dest;
    }

    static IssueHistoryItemDTO from(SourceJiraIssueDTO.SourceJiraHistoryItemDTO src) {
        if (src == null) {
            return null;
        }
        IssueHistoryItemDTO dest = new IssueHistoryItemDTO();
        dest.field = src.field;
        dest.fieldtype = src.fieldtype;
        dest.from = src.from;
        dest.fromString = src.fromString;
        dest.to = src.to;
        dest.toString = src.toString;
        return dest;
    }

    /** "name", or "name (key)" when the user's key differs from its name. */
    static String userDisplay(SourceJiraIssueDTO.SourceJiraUserDTO user) {
        if (user == null) {
            return null;
        }
        String name = user.name;
        String key = user.key;
        if (name == null) {
            return key;
        }
        return (key != null && !key.equals(name)) ? name + " (" + key + ")" : name;
    }
}
