package fr.an.jira.github.service;

import fr.an.jira.github.repository.GitHubPullRequestRepository;
import fr.an.jira.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.jira.github.rest.dtos.UserGitHubPullRequestStatsDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class GitHubPullRequestService {

    private static final String UNKNOWN_USER = "unknown";

    private final GitHubPullRequestRepository repository;

    public GitHubPullRequestService(GitHubPullRequestRepository repository) {
        this.repository = repository;
    }

    /** Finds a single PR by its number, or returns null if not found. */
    public GitHubPullRequestDTO findByNumber(int number) {
        return repository.findByNumber(number);
    }

    /** Lists the PRs created between fromYear and toYear (inclusive), optionally filtered by author login. */
    public List<GitHubPullRequestDTO> queryPullRequests(int fromYear, int toYear, String usernamePatternText) {
        return queryPullRequests(fromYear, toYear, usernamePatternText, null, null, null);
    }

    /** Lists the PRs created between fromYear and toYear (inclusive), optionally filtered by author login,
     * PR number range, and/or a regex on the PR number (as text). */
    public List<GitHubPullRequestDTO> queryPullRequests(int fromYear, int toYear, String usernamePatternText,
            Integer fromPullRequestNumber, Integer toPullRequestNumber, String pullRequestNumberPatternText) {
        List<GitHubPullRequestDTO> result = new ArrayList<>();
        Pattern usernamePattern = (usernamePatternText != null && !usernamePatternText.isBlank())
                ? Pattern.compile(usernamePatternText) : null;
        Pattern pullRequestNumberPattern = (pullRequestNumberPatternText != null && !pullRequestNumberPatternText.isBlank())
                ? Pattern.compile(pullRequestNumberPatternText) : null;
        repository.scanPullRequests(fromYear, toYear, (year, pr) -> {
            boolean matches = (usernamePattern == null || usernamePattern.matcher(authorOf(pr)).matches())
                    && (fromPullRequestNumber == null || pr.number >= fromPullRequestNumber)
                    && (toPullRequestNumber == null || pr.number <= toPullRequestNumber)
                    && (pullRequestNumberPattern == null || pullRequestNumberPattern.matcher(String.valueOf(pr.number)).matches());
            if (matches) {
                result.add(pr);
            }
        });
        return result;
    }

    private static String authorOf(GitHubPullRequestDTO pr) {
        String login = pr.authorLogin;
        return login != null && !login.isBlank() ? login : UNKNOWN_USER;
    }

    /** Count PRs created per author, for PRs created between fromYear and toYear (inclusive), optionally filtered by author login. */
    public Collection<UserGitHubPullRequestStatsDTO> queryUserPullRequestStats(
            int fromYear, int toYear, String usernamePatternText) {
        Map<String, UserGitHubPullRequestStatsDTO> tmp = new LinkedHashMap<>();
        Pattern usernamePattern = (usernamePatternText != null && !usernamePatternText.isBlank())
                ? Pattern.compile(usernamePatternText) : null;
        repository.scanPullRequests(fromYear, toYear, (year, pr) -> {
            String user = authorOf(pr);
            if (usernamePattern != null && !usernamePattern.matcher(user).matches()) {
                return;
            }
            UserGitHubPullRequestStatsDTO statPerUser = tmp.computeIfAbsent(user, UserGitHubPullRequestStatsDTO::new);
            statPerUser.add(year, pr);
        });
        return tmp.values();
    }
}
