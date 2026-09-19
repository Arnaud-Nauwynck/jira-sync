package fr.an.projectanalysis.github.service;

import fr.an.projectanalysis.github.rest.dtos.GitHubPrCriteriaDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.util.AnnotatedCritUtils;
import fr.an.projectanalysis.util.CritUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Whether a {@link GitHubPullRequestDTO} matches the Data Fetching + Main/Analysis/Development
 * Work/Personal Interest filter criteria of the github-pull-requests page (a null criteria
 * matches everything).
 */
public class GitHubPrCriteria implements Predicate<GitHubPullRequestDTO> {

    private static final String UNKNOWN_USER = "unknown";

    /** Default "created_year" partition range, when the criteria does not restrict it. */
    private static final int DEFAULT_FROM_YEAR = 2020;
    private static final int DEFAULT_TO_YEAR = 2050;

    private final GitHubPrCriteriaDTO c;

    private final Pattern usernamePattern;

    private final Pattern pullRequestNumberPattern;

    private final Pattern mergeableStatePattern;

    public GitHubPrCriteria(GitHubPrCriteriaDTO c) {
        this.c = c;
        this.usernamePattern = c != null ? CritUtils.compilePattern(c.usernamePattern) : null;
        this.pullRequestNumberPattern = c != null ? CritUtils.compilePattern(c.pullRequestNumberPattern) : null;
        this.mergeableStatePattern = c != null ? CritUtils.compilePattern(c.mergeableStatePattern) : null;
    }

    /** Criteria filtering only on the author login regex. */
    public static GitHubPrCriteria ofUsernamePattern(String usernamePatternText) {
        GitHubPrCriteriaDTO c = new GitHubPrCriteriaDTO();
        c.usernamePattern = usernamePatternText;
        return new GitHubPrCriteria(c);
    }

    /** Earliest "created_year" partition to scan (inclusive), defaulting to {@value #DEFAULT_FROM_YEAR}. */
    public int getFromYear() {
        return (c != null && c.fromYear != null) ? c.fromYear : DEFAULT_FROM_YEAR;
    }

    /** Latest "created_year" partition to scan (inclusive), defaulting to {@value #DEFAULT_TO_YEAR}. */
    public int getToYear() {
        return (c != null && c.toYear != null) ? c.toYear : DEFAULT_TO_YEAR;
    }

    /** The PR's author login, or {@code "unknown"} if it has none. */
    public static String authorOf(GitHubPullRequestDTO pr) {
        String login = pr.authorLogin;
        return login != null && !login.isBlank() ? login : UNKNOWN_USER;
    }

    @Override
    public boolean test(GitHubPullRequestDTO pr) {
        if (c == null) {
            return true;
        }
        if (!CritUtils.matchesRegex(usernamePattern, authorOf(pr))) {
            return false;
        }
        if (!CritUtils.matchesNumberRange(c.fromPullRequestNumber, c.toPullRequestNumber, pr.number)) {
            return false;
        }
        if (!CritUtils.matchesRegex(pullRequestNumberPattern, String.valueOf(pr.number))) {
            return false;
        }
        if (!CritUtils.matchesAny(c.titleContains, pr.title)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.bodyContains, pr.body)) {
            return false;
        }
        List<String> authorValues = new ArrayList<>();
        authorValues.add(pr.authorLogin);
        if (pr.assigneeLogins != null) {
            authorValues.addAll(pr.assigneeLogins);
        }
        if (pr.requestedReviewerLogins != null) {
            authorValues.addAll(pr.requestedReviewerLogins);
        }
        if (!CritUtils.matchesAny(c.authorContains, authorValues.toArray(String[]::new))) {
            return false;
        }
        List<String> labels = pr.labelNames != null ? pr.labelNames : List.of();
        if (!CritUtils.matchesAny(c.labelContains, labels.toArray(String[]::new))) {
            return false;
        }
        if (!CritUtils.matchesAny(c.baseRefContains, pr.baseRef)) {
            return false;
        }
        if (CritUtils.isExcluded(c.excludedStates, pr.state)) {
            return false;
        }
        if (!CritUtils.matchesAvailability(c.draftAvailability, pr.draft)) {
            return false;
        }
        if (!CritUtils.matchesAvailability(c.mergedAvailability, pr.merged)) {
            return false;
        }
        if (!CritUtils.matchesAvailability(c.mergeableAvailability, Boolean.TRUE.equals(pr.mergeable))) {
            return false;
        }
        if (!CritUtils.matchesRegex(mergeableStatePattern, pr.mergeableState)) {
            return false;
        }

        return AnnotatedCritUtils.matchesAnnotations(c, pr.annotated);
    }

}
