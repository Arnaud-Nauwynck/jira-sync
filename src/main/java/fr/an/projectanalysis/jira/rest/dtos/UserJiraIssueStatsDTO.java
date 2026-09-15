package fr.an.projectanalysis.jira.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserJiraIssueStatsDTO {

    /** Status names (lower-case) considered "closed", when no resolutiondate is set either. */
    private static final Set<String> CLOSED_STATUS_NAMES = Set.of("closed", "done", "resolved");

    /** Resolution names (lower-case) considered a rejection rather than a fix. */
    private static final Set<String> REJECTED_RESOLUTION_NAMES = Set.of(
            "won't fix", "wont fix", "won't do", "wont do",
            "cannot reproduce", "can not reproduce", "duplicate",
            "incomplete", "invalid", "rejected", "not a bug", "works as designed", "abandoned");

    public String user;

    public int issueCreateCount;
    public int closedIssuesCount;
    public int closedBySelfIssuesCount;
    public int closedForOtherIssuesCount;
    public int openIssuesCount;
    public int rejectedIssuesCount;
    public int reopenedIssuesCount;
    public int issuesCommentsCount;

    public Map<String,UserIssueCreatePerYearStatsDTO> perYear = new LinkedHashMap<>();

    public void add(int year, JiraIssueDTO issue) {
        this.issueCreateCount++;
        apply(analyze(this.user, issue));
        UserIssueCreatePerYearStatsDTO perUserPerYear = this.perYear.computeIfAbsent(Integer.toString(year), y -> new UserIssueCreatePerYearStatsDTO(Integer.parseInt(y)));
        perUserPerYear.add(this.user, issue);
    }

    private void apply(IssueOutcome o) {
        if (o.closed) {
            this.closedIssuesCount++;
            if (o.closedBySelf) {
                this.closedBySelfIssuesCount++;
            } else {
                this.closedForOtherIssuesCount++;
            }
            if (o.rejected) {
                this.rejectedIssuesCount++;
            }
        }
        if (o.reopened) {
            this.reopenedIssuesCount++;
        }
        this.issuesCommentsCount += o.commentsCount;
        this.openIssuesCount = this.issueCreateCount - this.closedIssuesCount;
    }


    @NoArgsConstructor
    public static class UserIssueCreatePerYearStatsDTO {

        /** Number of issue keys to sample into {@link #firstIssues}. */
        private static final int FIRST_ISSUES_SAMPLE_SIZE = 2;

        public int year;

        public int issueCreateCount;
        public int closedIssuesCount;
        public int closedBySelfIssuesCount;
        public int closedForOtherIssuesCount;
        public int openIssuesCount;
        public int rejectedIssuesCount;
        public int reopenedIssuesCount;
        public int issuesCommentsCount;

        /** A sampling of the first {@value #FIRST_ISSUES_SAMPLE_SIZE} issue keys added for this year. */
        public List<String> firstIssues = new ArrayList<>();

        public UserIssueCreatePerYearStatsDTO(int year) {
            this.year = year;
        }

        public void add(String user, JiraIssueDTO issue) {
            this.issueCreateCount++;
            if (this.firstIssues.size() < FIRST_ISSUES_SAMPLE_SIZE) {
                this.firstIssues.add(issue.key);
            }
            apply(analyze(user, issue));
        }

        private void apply(IssueOutcome o) {
            if (o.closed) {
                this.closedIssuesCount++;
                if (o.closedBySelf) {
                    this.closedBySelfIssuesCount++;
                } else {
                    this.closedForOtherIssuesCount++;
                }
                if (o.rejected) {
                    this.rejectedIssuesCount++;
                }
            }
            if (o.reopened) {
                this.reopenedIssuesCount++;
            }
            this.issuesCommentsCount += o.commentsCount;
            this.openIssuesCount = this.issueCreateCount - this.closedIssuesCount;
        }
    }

    public UserJiraIssueStatsDTO(String user) {
        this.user = user;
    }

    /** Outcome of analyzing a single issue, applied identically to the global and per-year counters. */
    private static final class IssueOutcome {
        final boolean closed;
        final boolean closedBySelf;
        final boolean rejected;
        final boolean reopened;
        final int commentsCount;

        IssueOutcome(boolean closed, boolean closedBySelf, boolean rejected, boolean reopened, int commentsCount) {
            this.closed = closed;
            this.closedBySelf = closedBySelf;
            this.rejected = rejected;
            this.reopened = reopened;
            this.commentsCount = commentsCount;
        }
    }

    private static IssueOutcome analyze(String user, JiraIssueDTO issue) {
        JiraIssueDTO.IssueFieldsDTO f = issue.fields;
        String status = f != null ? f.status : null;
        String resolution = f != null ? f.resolution : null;
        String resolutiondate = f != null ? f.resolutiondate : null;

        boolean closed = (resolutiondate != null && !resolutiondate.isBlank())
                || (status != null && CLOSED_STATUS_NAMES.contains(status.toLowerCase(Locale.ROOT)));

        boolean rejected = closed && resolution != null
                && REJECTED_RESOLUTION_NAMES.contains(resolution.toLowerCase(Locale.ROOT));

        boolean closedBySelf = false;
        if (closed) {
            String closer = closerOf(issue);
            closedBySelf = closer != null && closer.equalsIgnoreCase(user);
        }

        int commentsCount = (f != null && f.comments != null) ? f.comments.size() : 0;

        boolean reopened = wasReopened(issue);

        return new IssueOutcome(closed, closedBySelf, rejected, reopened, commentsCount);
    }

    /** Whether the issue's history shows a status transition out of a closed-like status. */
    private static boolean wasReopened(JiraIssueDTO issue) {
        List<JiraIssueDTO.IssueHistoryDTO> histories = issue.histories;
        if (histories == null) {
            return false;
        }
        for (JiraIssueDTO.IssueHistoryDTO history : histories) {
            if (history.items == null) {
                continue;
            }
            for (JiraIssueDTO.IssueHistoryItemDTO item : history.items) {
                if (item.field == null || !"status".equalsIgnoreCase(item.field)) {
                    continue;
                }
                boolean fromClosed = item.fromString != null
                        && CLOSED_STATUS_NAMES.contains(item.fromString.toLowerCase(Locale.ROOT));
                boolean toClosed = item.toString != null
                        && CLOSED_STATUS_NAMES.contains(item.toString.toLowerCase(Locale.ROOT));
                if (fromClosed && !toClosed) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The user who most recently transitioned the issue's status/resolution, from the change
     * history; falls back to the current assignee when no such history entry is found.
     */
    private static String closerOf(JiraIssueDTO issue) {
        List<JiraIssueDTO.IssueHistoryDTO> histories = issue.histories;
        if (histories != null) {
            for (int i = histories.size() - 1; i >= 0; i--) {
                JiraIssueDTO.IssueHistoryDTO history = histories.get(i);
                if (history.items == null) {
                    continue;
                }
                for (JiraIssueDTO.IssueHistoryItemDTO item : history.items) {
                    if (item.field != null
                            && ("status".equalsIgnoreCase(item.field) || "resolution".equalsIgnoreCase(item.field))) {
                        return history.author;
                    }
                }
            }
        }
        return issue.fields != null ? issue.fields.assignee : null;
    }

}
