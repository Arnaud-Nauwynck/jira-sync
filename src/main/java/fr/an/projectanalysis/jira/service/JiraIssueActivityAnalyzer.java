package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.rest.dtos.UserActivityStatsDTO;
import fr.an.projectanalysis.util.DateTimeUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Extracts the create/comment/update/close events of a single {@link JiraIssueDTO} and adds them
 * into the per-user, per-month {@link UserActivityStatsDTO} accumulator, keyed by the user who
 * performed each event (creator, comment author, history author) and the month it occurred in.
 */
public final class JiraIssueActivityAnalyzer {

    private static final String UNKNOWN_USER = "unknown";

    /** Status names (lower-case) considered "closed" (mirrors {@code UserJiraIssueStatsDTO}). */
    private static final Set<String> CLOSED_STATUS_NAMES = Set.of("closed", "done", "resolved");

    /** Resolution names (lower-case) considered a rejection rather than a fix (mirrors {@code UserJiraIssueStatsDTO}). */
    private static final Set<String> REJECTED_RESOLUTION_NAMES = Set.of(
            "won't fix", "wont fix", "won't do", "wont do",
            "cannot reproduce", "can not reproduce", "duplicate",
            "incomplete", "invalid", "rejected", "not a bug", "works as designed", "abandoned");

    private JiraIssueActivityAnalyzer() {
    }

    public static void contribute(Map<String, UserActivityStatsDTO> acc, JiraIssueDTO issue) {
        JiraIssueDTO.IssueFieldsDTO fields = issue.fields;
        if (fields == null) {
            return;
        }
        String creator = JiraIssueCriteria.creatorOf(issue);

        String createdMonth = DateTimeUtils.monthOf(fields.created);
        if (createdMonth != null) {
            statsOf(acc, creator).addJiraIssueCreated(createdMonth);
        }

        if (fields.comments != null) {
            for (JiraIssueDTO.IssueCommentDTO comment : fields.comments) {
                String month = DateTimeUtils.monthOf(comment.created);
                if (month != null) {
                    statsOf(acc, orFallback(comment.author, creator)).addJiraIssueCommented(month);
                }
            }
        }

        contributeHistories(acc, issue, creator);
    }

    private static void contributeHistories(Map<String, UserActivityStatsDTO> acc, JiraIssueDTO issue, String creator) {
        List<JiraIssueDTO.IssueHistoryDTO> histories = issue.histories;
        if (histories == null) {
            return;
        }
        for (JiraIssueDTO.IssueHistoryDTO history : histories) {
            List<JiraIssueDTO.IssueHistoryItemDTO> items = history.items;
            if (items == null || items.isEmpty()) {
                continue;
            }
            String month = DateTimeUtils.monthOf(history.created);
            if (month == null) {
                continue;
            }
            String author = orFallback(history.author, creator);
            statsOf(acc, author).addJiraIssueUpdated(month);

            String statusFrom = null;
            String statusTo = null;
            String resolutionTo = null;
            for (JiraIssueDTO.IssueHistoryItemDTO item : items) {
                if (item.field == null) {
                    continue;
                }
                if ("status".equalsIgnoreCase(item.field)) {
                    statusFrom = item.fromString;
                    statusTo = item.toString;
                } else if ("resolution".equalsIgnoreCase(item.field)) {
                    resolutionTo = item.toString;
                }
            }
            boolean becameClosed = statusTo != null && isClosedStatus(statusTo)
                    && !(statusFrom != null && isClosedStatus(statusFrom));
            if (becameClosed) {
                String resolution = resolutionTo != null ? resolutionTo : issue.fields.resolution;
                if (resolution != null && isRejectedResolution(resolution)) {
                    statsOf(acc, author).addJiraIssueClosedRejected(month);
                } else {
                    statsOf(acc, author).addJiraIssueCloseResolved(month);
                }
            }
        }
    }

    private static boolean isClosedStatus(String status) {
        return CLOSED_STATUS_NAMES.contains(status.toLowerCase(Locale.ROOT));
    }

    private static boolean isRejectedResolution(String resolution) {
        return REJECTED_RESOLUTION_NAMES.contains(resolution.toLowerCase(Locale.ROOT));
    }

    private static String orFallback(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private static UserActivityStatsDTO statsOf(Map<String, UserActivityStatsDTO> acc, String user) {
        String key = (user != null && !user.isBlank()) ? user : UNKNOWN_USER;
        return acc.computeIfAbsent(key, UserActivityStatsDTO::new);
    }

}
