package fr.an.jira.github.service;

import fr.an.jira.github.repository.GitHubPullRequestRepository;
import fr.an.jira.github.rest.dtos.GitHubPullRequestDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
        List<GitHubPullRequestDTO> result = new ArrayList<>();
        Pattern usernamePattern = (usernamePatternText != null && !usernamePatternText.isBlank())
                ? Pattern.compile(usernamePatternText) : null;
        repository.scanPullRequests(fromYear, toYear, (year, pr) -> {
            if (usernamePattern == null || usernamePattern.matcher(authorOf(pr)).matches()) {
                result.add(pr);
            }
        });
        return result;
    }

    private static String authorOf(GitHubPullRequestDTO pr) {
        String login = pr.authorLogin;
        return login != null && !login.isBlank() ? login : UNKNOWN_USER;
    }
}
