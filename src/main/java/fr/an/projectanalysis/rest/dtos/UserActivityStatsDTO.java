package fr.an.projectanalysis.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Combined per-user activity stats across Jira issues, GitHub pull requests and mailing-list
 * messages, broken down per calendar month ("yyyy-MM") in {@link #perMonth}. Built by
 * {@code fr.an.projectanalysis.service.UserActivityStatsService}, fed by each source's own
 * analyzer ({@code JiraIssueActivityAnalyzer}, {@code GitHubPullRequestActivityAnalyzer},
 * {@code MailMessageActivityAnalyzer}).
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserActivityStatsDTO {

    public String user;

    public int jiraIssueCreatedCount;
    public int jiraIssueUpdatedCount;
    public int jiraIssueCommentedCount;
    public int jiraIssueClosedRejectedCount;
    public int jiraIssueCloseResolvedCount;

    public int githubPullRequestCreatedCount;
    public int githubPullRequestUpdatedCount;
    public int githubPullRequestCommentedCount;
    public int githubPullRequestMergedCount;
    public int githubPullRequestClosedCount;

    public int mailMessageSentCount;
    public int mailMessageRepliedCount;
    public int mailMessageVotedCount;

    public Map<String, UserActivityMonthStatsDTO> perMonth = new LinkedHashMap<>();

    public UserActivityStatsDTO(String user) {
        this.user = user;
    }

    public void addJiraIssueCreated(String month) { jiraIssueCreatedCount++; perMonth(month).jiraIssueCreatedCount++; }
    public void addJiraIssueUpdated(String month) { jiraIssueUpdatedCount++; perMonth(month).jiraIssueUpdatedCount++; }
    public void addJiraIssueCommented(String month) { jiraIssueCommentedCount++; perMonth(month).jiraIssueCommentedCount++; }
    public void addJiraIssueClosedRejected(String month) { jiraIssueClosedRejectedCount++; perMonth(month).jiraIssueClosedRejectedCount++; }
    public void addJiraIssueCloseResolved(String month) { jiraIssueCloseResolvedCount++; perMonth(month).jiraIssueCloseResolvedCount++; }

    public void addGithubPullRequestCreated(String month) { githubPullRequestCreatedCount++; perMonth(month).githubPullRequestCreatedCount++; }
    public void addGithubPullRequestUpdated(String month) { githubPullRequestUpdatedCount++; perMonth(month).githubPullRequestUpdatedCount++; }
    public void addGithubPullRequestCommented(String month) { githubPullRequestCommentedCount++; perMonth(month).githubPullRequestCommentedCount++; }
    public void addGithubPullRequestMerged(String month) { githubPullRequestMergedCount++; perMonth(month).githubPullRequestMergedCount++; }
    public void addGithubPullRequestClosed(String month) { githubPullRequestClosedCount++; perMonth(month).githubPullRequestClosedCount++; }

    public void addMailMessageSent(String month) { mailMessageSentCount++; perMonth(month).mailMessageSentCount++; }
    public void addMailMessageReplied(String month) { mailMessageRepliedCount++; perMonth(month).mailMessageRepliedCount++; }
    public void addMailMessageVoted(String month) { mailMessageVotedCount++; perMonth(month).mailMessageVotedCount++; }

    private UserActivityMonthStatsDTO perMonth(String month) {
        return perMonth.computeIfAbsent(month, UserActivityMonthStatsDTO::new);
    }

    /** Total count of all activity metrics, for ranking users by overall activity. */
    public int totalCount() {
        return jiraIssueCreatedCount + jiraIssueUpdatedCount + jiraIssueCommentedCount
                + jiraIssueClosedRejectedCount + jiraIssueCloseResolvedCount
                + githubPullRequestCreatedCount + githubPullRequestUpdatedCount + githubPullRequestCommentedCount
                + githubPullRequestMergedCount + githubPullRequestClosedCount
                + mailMessageSentCount + mailMessageRepliedCount + mailMessageVotedCount;
    }

    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserActivityMonthStatsDTO {

        public String month;

        public int jiraIssueCreatedCount;
        public int jiraIssueUpdatedCount;
        public int jiraIssueCommentedCount;
        public int jiraIssueClosedRejectedCount;
        public int jiraIssueCloseResolvedCount;

        public int githubPullRequestCreatedCount;
        public int githubPullRequestUpdatedCount;
        public int githubPullRequestCommentedCount;
        public int githubPullRequestMergedCount;
        public int githubPullRequestClosedCount;

        public int mailMessageSentCount;
        public int mailMessageRepliedCount;
        public int mailMessageVotedCount;

        public UserActivityMonthStatsDTO(String month) {
            this.month = month;
        }
    }

}
