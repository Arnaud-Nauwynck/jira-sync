package fr.an.projectanalysis.github.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserGitHubPullRequestStatsDTO {

    public String user;

    public int pullRequestCreateCount;
    public int mergedCount;
    public int closedNotMergedCount;
    public int openCount;
    public int draftCount;
    public int commentsCount;
    public int reviewCommentsCount;
    public int commitsCount;
    public int additionsCount;
    public int deletionsCount;
    public int changedFilesCount;

    public Map<String,UserPullRequestCreatePerYearStatsDTO> perYear = new LinkedHashMap<>();

    public UserGitHubPullRequestStatsDTO(String user) {
        this.user = user;
    }

    public void add(int year, GitHubPullRequestDTO pr) {
        this.pullRequestCreateCount++;
        apply(analyze(pr));
        UserPullRequestCreatePerYearStatsDTO perUserPerYear = this.perYear.computeIfAbsent(
                Integer.toString(year), y -> new UserPullRequestCreatePerYearStatsDTO(Integer.parseInt(y)));
        perUserPerYear.add(pr);
    }

    private void apply(PullRequestOutcome o) {
        if (o.merged) {
            this.mergedCount++;
        } else if (o.closed) {
            this.closedNotMergedCount++;
        }
        if (o.draft) {
            this.draftCount++;
        }
        this.commentsCount += o.commentsCount;
        this.reviewCommentsCount += o.reviewCommentsCount;
        this.commitsCount += o.commitsCount;
        this.additionsCount += o.additionsCount;
        this.deletionsCount += o.deletionsCount;
        this.changedFilesCount += o.changedFilesCount;
        this.openCount = this.pullRequestCreateCount - this.mergedCount - this.closedNotMergedCount;
    }

    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserPullRequestCreatePerYearStatsDTO {

        /** Number of PR numbers to sample into {@link #firstPullRequests}. */
        private static final int FIRST_PULL_REQUESTS_SAMPLE_SIZE = 2;

        public int year;

        public int pullRequestCreateCount;
        public int mergedCount;
        public int closedNotMergedCount;
        public int openCount;
        public int draftCount;
        public int commentsCount;
        public int reviewCommentsCount;
        public int commitsCount;
        public int additionsCount;
        public int deletionsCount;
        public int changedFilesCount;

        /** A sampling of the first {@value #FIRST_PULL_REQUESTS_SAMPLE_SIZE} PR numbers added for this year. */
        public List<Integer> firstPullRequests = new ArrayList<>();

        public UserPullRequestCreatePerYearStatsDTO(int year) {
            this.year = year;
        }

        public void add(GitHubPullRequestDTO pr) {
            this.pullRequestCreateCount++;
            if (this.firstPullRequests.size() < FIRST_PULL_REQUESTS_SAMPLE_SIZE) {
                this.firstPullRequests.add(pr.number);
            }
            apply(analyze(pr));
        }

        private void apply(PullRequestOutcome o) {
            if (o.merged) {
                this.mergedCount++;
            } else if (o.closed) {
                this.closedNotMergedCount++;
            }
            if (o.draft) {
                this.draftCount++;
            }
            this.commentsCount += o.commentsCount;
            this.reviewCommentsCount += o.reviewCommentsCount;
            this.commitsCount += o.commitsCount;
            this.additionsCount += o.additionsCount;
            this.deletionsCount += o.deletionsCount;
            this.changedFilesCount += o.changedFilesCount;
            this.openCount = this.pullRequestCreateCount - this.mergedCount - this.closedNotMergedCount;
        }
    }

    /** Outcome of analyzing a single pull request, applied identically to the global and per-year counters. */
    private static final class PullRequestOutcome {
        final boolean closed;
        final boolean merged;
        final boolean draft;
        final int commentsCount;
        final int reviewCommentsCount;
        final int commitsCount;
        final int additionsCount;
        final int deletionsCount;
        final int changedFilesCount;

        PullRequestOutcome(boolean closed, boolean merged, boolean draft,
                int commentsCount, int reviewCommentsCount, int commitsCount,
                int additionsCount, int deletionsCount, int changedFilesCount) {
            this.closed = closed;
            this.merged = merged;
            this.draft = draft;
            this.commentsCount = commentsCount;
            this.reviewCommentsCount = reviewCommentsCount;
            this.commitsCount = commitsCount;
            this.additionsCount = additionsCount;
            this.deletionsCount = deletionsCount;
            this.changedFilesCount = changedFilesCount;
        }
    }

    private static PullRequestOutcome analyze(GitHubPullRequestDTO pr) {
        boolean merged = pr.merged || (pr.mergedAt != null && !pr.mergedAt.isBlank());
        boolean closed = merged || "closed".equalsIgnoreCase(pr.state)
                || (pr.closedAt != null && !pr.closedAt.isBlank());

        return new PullRequestOutcome(closed, merged, pr.draft,
                pr.comments != null ? pr.comments : 0,
                pr.reviewComments != null ? pr.reviewComments : 0,
                pr.commits != null ? pr.commits : 0,
                pr.additions != null ? pr.additions : 0,
                pr.deletions != null ? pr.deletions : 0,
                pr.changedFiles != null ? pr.changedFiles : 0);
    }

}
