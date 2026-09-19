package fr.an.projectanalysis.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-user Jira issue activity stats, broken down per calendar month ("yyyy-MM") in
 * {@link #perMonth}. Built by {@code fr.an.projectanalysis.service.UserActivityStatsService},
 * fed by {@code JiraIssueActivityAnalyzer}.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JiraUserActivityStatsDTO {

    public String user;

    public int jiraIssueCreatedCount;
    public int jiraIssueUpdatedCount;
    public int jiraIssueCommentedCount;
    public int jiraIssueClosedRejectedCount;
    public int jiraIssueCloseResolvedCount;

    public Map<String, JiraUserActivityMonthStatsDTO> perMonth = new LinkedHashMap<>();

    public JiraUserActivityStatsDTO(String user) {
        this.user = user;
    }

    public void addJiraIssueCreated(String month) { jiraIssueCreatedCount++; perMonth(month).jiraIssueCreatedCount++; }
    public void addJiraIssueUpdated(String month) { jiraIssueUpdatedCount++; perMonth(month).jiraIssueUpdatedCount++; }
    public void addJiraIssueCommented(String month) { jiraIssueCommentedCount++; perMonth(month).jiraIssueCommentedCount++; }
    public void addJiraIssueClosedRejected(String month) { jiraIssueClosedRejectedCount++; perMonth(month).jiraIssueClosedRejectedCount++; }
    public void addJiraIssueCloseResolved(String month) { jiraIssueCloseResolvedCount++; perMonth(month).jiraIssueCloseResolvedCount++; }

    private JiraUserActivityMonthStatsDTO perMonth(String month) {
        return perMonth.computeIfAbsent(month, JiraUserActivityMonthStatsDTO::new);
    }

    /** Total count of all Jira activity metrics, for ranking users by Jira activity. */
    public int totalCount() {
        return jiraIssueCreatedCount + jiraIssueUpdatedCount + jiraIssueCommentedCount
                + jiraIssueClosedRejectedCount + jiraIssueCloseResolvedCount;
    }

    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JiraUserActivityMonthStatsDTO {

        public String month;

        public int jiraIssueCreatedCount;
        public int jiraIssueUpdatedCount;
        public int jiraIssueCommentedCount;
        public int jiraIssueClosedRejectedCount;
        public int jiraIssueCloseResolvedCount;

        public JiraUserActivityMonthStatsDTO(String month) {
            this.month = month;
        }
    }

}
