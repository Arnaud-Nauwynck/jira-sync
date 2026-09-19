package fr.an.projectanalysis.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-user GitHub pull-request activity stats, broken down per calendar month ("yyyy-MM") in
 * {@link #perMonth}. Built by {@code fr.an.projectanalysis.service.UserActivityStatsService},
 * fed by {@code GitHubPullRequestActivityAnalyzer}.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GithubUserActivityStatsDTO {

    public String user;

    public int githubPullRequestCreatedCount;
    public int githubPullRequestUpdatedCount;
    public int githubPullRequestCommentedCount;
    public int githubPullRequestMergedCount;
    public int githubPullRequestClosedCount;

    public Map<String, GithubUserActivityMonthStatsDTO> perMonth = new LinkedHashMap<>();

    public GithubUserActivityStatsDTO(String user) {
        this.user = user;
    }

    public void addGithubPullRequestCreated(String month) { githubPullRequestCreatedCount++; perMonth(month).githubPullRequestCreatedCount++; }
    public void addGithubPullRequestUpdated(String month) { githubPullRequestUpdatedCount++; perMonth(month).githubPullRequestUpdatedCount++; }
    public void addGithubPullRequestCommented(String month) { githubPullRequestCommentedCount++; perMonth(month).githubPullRequestCommentedCount++; }
    public void addGithubPullRequestMerged(String month) { githubPullRequestMergedCount++; perMonth(month).githubPullRequestMergedCount++; }
    public void addGithubPullRequestClosed(String month) { githubPullRequestClosedCount++; perMonth(month).githubPullRequestClosedCount++; }

    private GithubUserActivityMonthStatsDTO perMonth(String month) {
        return perMonth.computeIfAbsent(month, GithubUserActivityMonthStatsDTO::new);
    }

    /** Total count of all GitHub activity metrics, for ranking users by GitHub activity. */
    public int totalCount() {
        return githubPullRequestCreatedCount + githubPullRequestUpdatedCount + githubPullRequestCommentedCount
                + githubPullRequestMergedCount + githubPullRequestClosedCount;
    }

    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GithubUserActivityMonthStatsDTO {

        public String month;

        public int githubPullRequestCreatedCount;
        public int githubPullRequestUpdatedCount;
        public int githubPullRequestCommentedCount;
        public int githubPullRequestMergedCount;
        public int githubPullRequestClosedCount;

        public GithubUserActivityMonthStatsDTO(String month) {
            this.month = month;
        }
    }

}
