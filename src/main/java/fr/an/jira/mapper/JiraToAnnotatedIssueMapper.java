package fr.an.jira.mapper;

import fr.an.jira.client.dtos.JiraIssueDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraChangelogDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraCommentDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraCommentsDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraFieldsDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraHistoryDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraHistoryItemDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraIssueLinkDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraIssueLinkTypeDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraLinkedIssueDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraLinkedIssueFieldsDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraProgressDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraVotesDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraWatchesDTO;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO.AnnotatedJiraWorklogDTO;

import java.util.stream.Collectors;

/**
 * Converts the raw {@link JiraIssueDTO} class hierarchy (mirroring the Jira REST API JSON)
 * into the flattened {@link AnnotatedJiraIssueDTO} hierarchy.
 * Does not set {@link AnnotatedJiraIssueDTO#annotated}, which carries data enriched/persisted
 * locally rather than coming from the source Jira server.
 */
public class JiraToAnnotatedIssueMapper {

    private JiraToAnnotatedIssueMapper() {
    }

    public static AnnotatedJiraIssueDTO from(JiraIssueDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraIssueDTO dest = new AnnotatedJiraIssueDTO();
        dest.expand = src.expand;
        dest.id = src.id;
        dest.self = src.self;
        dest.key = src.key;
        dest.fields = from(src.fields);
        dest.changelog = from(src.changelog);
        return dest;
    }

    static AnnotatedJiraFieldsDTO from(JiraIssueDTO.JiraFieldsDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraFieldsDTO dest = new AnnotatedJiraFieldsDTO();
        dest.fixVersions = src.fixVersions;
        dest.resolution = (src.resolution != null) ? src.resolution.name : null;
        dest.lastViewed = src.lastViewed;
        dest.priority = (src.priority != null) ? src.priority.name : null;
        dest.labels = src.labels;
        dest.aggregatetimeoriginalestimate = src.aggregatetimeoriginalestimate;
        dest.timeestimate = src.timeestimate;
        dest.versions = src.versions;
        dest.issuelinks = src.issuelinks == null ? null
                : src.issuelinks.stream().map(JiraToAnnotatedIssueMapper::from).collect(Collectors.toList());
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
        dest.votes = from(src.votes);
        dest.worklog = from(src.worklog);
        dest.archivedby = src.archivedby;
        dest.issuetype = (src.issuetype != null) ? src.issuetype.name : null;
        dest.timespent = src.timespent;
        dest.project = (src.project != null) ? src.project.name : null;
        dest.aggregatetimespent = src.aggregatetimespent;
        dest.resolutiondate = src.resolutiondate;
        dest.workratio = src.workratio;
        dest.watches = from(src.watches);
        dest.created = src.created;
        dest.updated = src.updated;
        dest.timeoriginalestimate = src.timeoriginalestimate;
        dest.description = src.description;
        dest.summary = src.summary;
        dest.environment = src.environment;
        dest.duedate = src.duedate;
        dest.comment = from(src.comment);
        dest.timetracking = src.timetracking;
        dest.customFields = src.customFields;
        return dest;
    }

    static AnnotatedJiraIssueLinkDTO from(JiraIssueDTO.JiraIssueLinkDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraIssueLinkDTO dest = new AnnotatedJiraIssueLinkDTO();
        dest.id = src.id;
        dest.self = src.self;
        dest.type = from(src.type);
        dest.inwardIssue = from(src.inwardIssue);
        dest.outwardIssue = from(src.outwardIssue);
        return dest;
    }

    static AnnotatedJiraIssueLinkTypeDTO from(JiraIssueDTO.JiraIssueLinkTypeDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraIssueLinkTypeDTO dest = new AnnotatedJiraIssueLinkTypeDTO();
        dest.id = src.id;
        dest.self = src.self;
        dest.name = src.name;
        dest.inward = src.inward;
        dest.outward = src.outward;
        return dest;
    }

    static AnnotatedJiraLinkedIssueDTO from(JiraIssueDTO.JiraLinkedIssueDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraLinkedIssueDTO dest = new AnnotatedJiraLinkedIssueDTO();
        dest.id = src.id;
        dest.key = src.key;
        dest.self = src.self;
        dest.fields = from(src.fields);
        return dest;
    }

    static AnnotatedJiraLinkedIssueFieldsDTO from(JiraIssueDTO.JiraLinkedIssueFieldsDTO src) {
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

    static AnnotatedJiraProgressDTO from(JiraIssueDTO.JiraProgressDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraProgressDTO dest = new AnnotatedJiraProgressDTO();
        dest.progress = src.progress;
        dest.total = src.total;
        return dest;
    }

    static AnnotatedJiraVotesDTO from(JiraIssueDTO.JiraVotesDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraVotesDTO dest = new AnnotatedJiraVotesDTO();
        dest.self = src.self;
        dest.votes = src.votes;
        dest.hasVoted = src.hasVoted;
        return dest;
    }

    static AnnotatedJiraWorklogDTO from(JiraIssueDTO.JiraWorklogDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraWorklogDTO dest = new AnnotatedJiraWorklogDTO();
        dest.startAt = src.startAt;
        dest.maxResults = src.maxResults;
        dest.total = src.total;
        dest.worklogs = src.worklogs;
        return dest;
    }

    static AnnotatedJiraWatchesDTO from(JiraIssueDTO.JiraWatchesDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraWatchesDTO dest = new AnnotatedJiraWatchesDTO();
        dest.self = src.self;
        dest.watchCount = src.watchCount;
        dest.isWatching = src.isWatching;
        return dest;
    }

    static AnnotatedJiraCommentDTO from(JiraIssueDTO.JiraCommentDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraCommentDTO dest = new AnnotatedJiraCommentDTO();
        dest.self = src.self;
        dest.id = src.id;
        dest.author = userDisplay(src.author);
        dest.body = src.body;
        dest.updateAuthor = userDisplay(src.updateAuthor);
        dest.created = src.created;
        dest.updated = src.updated;
        return dest;
    }

    static AnnotatedJiraCommentsDTO from(JiraIssueDTO.JiraCommentsDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraCommentsDTO dest = new AnnotatedJiraCommentsDTO();
        dest.comments = src.comments == null ? null
                : src.comments.stream().map(JiraToAnnotatedIssueMapper::from).collect(Collectors.toList());
        dest.maxResults = src.maxResults;
        dest.total = src.total;
        dest.startAt = src.startAt;
        return dest;
    }

    static AnnotatedJiraChangelogDTO from(JiraIssueDTO.JiraChangelogDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraChangelogDTO dest = new AnnotatedJiraChangelogDTO();
        dest.startAt = src.startAt;
        dest.maxResults = src.maxResults;
        dest.total = src.total;
        dest.histories = src.histories == null ? null
                : src.histories.stream().map(JiraToAnnotatedIssueMapper::from).collect(Collectors.toList());
        return dest;
    }

    static AnnotatedJiraHistoryDTO from(JiraIssueDTO.JiraHistoryDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraHistoryDTO dest = new AnnotatedJiraHistoryDTO();
        dest.id = src.id;
        dest.author = userDisplay(src.author);
        dest.created = src.created;
        dest.items = src.items == null ? null
                : src.items.stream().map(JiraToAnnotatedIssueMapper::from).collect(Collectors.toList());
        return dest;
    }

    static AnnotatedJiraHistoryItemDTO from(JiraIssueDTO.JiraHistoryItemDTO src) {
        if (src == null) {
            return null;
        }
        AnnotatedJiraHistoryItemDTO dest = new AnnotatedJiraHistoryItemDTO();
        dest.field = src.field;
        dest.fieldtype = src.fieldtype;
        dest.from = src.from;
        dest.fromString = src.fromString;
        dest.to = src.to;
        dest.toString = src.toString;
        return dest;
    }

    /** "name", or "name (key)" when the user's key differs from its name. */
    static String userDisplay(JiraIssueDTO.JiraUserDTO user) {
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
