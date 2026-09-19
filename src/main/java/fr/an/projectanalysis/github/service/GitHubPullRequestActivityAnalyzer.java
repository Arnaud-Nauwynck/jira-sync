package fr.an.projectanalysis.github.service;

import fr.an.projectanalysis.github.rest.dtos.GitHubIssueCommentDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubIssueEventDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestReviewCommentDTO;
import fr.an.projectanalysis.rest.dtos.GithubUserActivityStatsDTO;
import fr.an.projectanalysis.util.DateTimeUtils;

import java.util.Map;

/**
 * Extracts the create/comment/update/merge/close events of a single {@link GitHubPullRequestDTO}
 * and adds them into the per-user, per-month {@link GithubUserActivityStatsDTO} accumulator, keyed
 * by the user who performed each event and the month it occurred in.
 */
public final class GitHubPullRequestActivityAnalyzer {

    private static final String CLOSED_STATE = "closed";
    private static final String CLOSED_EVENT = "closed";

    private GitHubPullRequestActivityAnalyzer() {
    }

    public static void contribute(Map<String, GithubUserActivityStatsDTO> acc, GitHubPullRequestDTO pr) {
        String author = GitHubPrCriteria.authorOf(pr);

        String createdMonth = DateTimeUtils.monthOf(pr.createdAt);
        if (createdMonth != null) {
            statsOf(acc, author).addGithubPullRequestCreated(createdMonth);
        }

        // No per-field update history is kept for GitHub PRs (unlike Jira issues' "histories"), so
        // a single "updated" event is credited to the author when the PR's last-update timestamp
        // differs from its creation month.
        String updatedMonth = DateTimeUtils.monthOf(pr.updatedAt);
        if (updatedMonth != null && !updatedMonth.equals(createdMonth)) {
            statsOf(acc, author).addGithubPullRequestUpdated(updatedMonth);
        }

        contributeComments(acc, pr, author);

        if (pr.merged || (pr.mergedAt != null && !pr.mergedAt.isBlank())) {
            String month = DateTimeUtils.monthOf(pr.mergedAt);
            if (month != null) {
                statsOf(acc, orFallback(pr.mergedByLogin, author)).addGithubPullRequestMerged(month);
            }
        } else if (CLOSED_STATE.equalsIgnoreCase(pr.state) || (pr.closedAt != null && !pr.closedAt.isBlank())) {
            String month = DateTimeUtils.monthOf(pr.closedAt);
            if (month != null) {
                statsOf(acc, closerOf(pr, author)).addGithubPullRequestClosed(month);
            }
        }
    }

    private static void contributeComments(Map<String, GithubUserActivityStatsDTO> acc, GitHubPullRequestDTO pr, String fallbackAuthor) {
        if (pr.commentsData != null) {
            for (GitHubIssueCommentDTO comment : pr.commentsData) {
                String month = DateTimeUtils.monthOf(comment.createdAt);
                if (month != null) {
                    statsOf(acc, orFallback(comment.authorLogin, fallbackAuthor)).addGithubPullRequestCommented(month);
                }
            }
        }
        if (pr.reviewCommentsData != null) {
            for (GitHubPullRequestReviewCommentDTO comment : pr.reviewCommentsData) {
                String month = DateTimeUtils.monthOf(comment.createdAt);
                if (month != null) {
                    statsOf(acc, orFallback(comment.authorLogin, fallbackAuthor)).addGithubPullRequestCommented(month);
                }
            }
        }
    }

    /** The actor of the PR's "closed" issue event (from the on-demand-loaded {@code issueEventsData}),
     * falling back to {@code fallback} (the PR author) when that data isn't loaded or has no such event. */
    private static String closerOf(GitHubPullRequestDTO pr, String fallback) {
        if (pr.issueEventsData != null) {
            for (int i = pr.issueEventsData.size() - 1; i >= 0; i--) {
                GitHubIssueEventDTO event = pr.issueEventsData.get(i);
                if (CLOSED_EVENT.equalsIgnoreCase(event.event) && event.actorLogin != null && !event.actorLogin.isBlank()) {
                    return event.actorLogin;
                }
            }
        }
        return fallback;
    }

    private static String orFallback(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private static GithubUserActivityStatsDTO statsOf(Map<String, GithubUserActivityStatsDTO> acc, String user) {
        return acc.computeIfAbsent(user, GithubUserActivityStatsDTO::new);
    }

}
