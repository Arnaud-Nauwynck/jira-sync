package fr.an.projectanalysis.github.service;

import fr.an.projectanalysis.github.repository.GitHubPullRequestRepository;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestAnnotationDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestExtraFieldsDTO;
import fr.an.projectanalysis.github.rest.dtos.UserGitHubPullRequestStatsDTO;
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
        return queryPullRequests(fromYear, toYear, usernamePatternText, null, null, null, null, null, null);
    }

    /** Lists the PRs created between fromYear and toYear (inclusive), optionally filtered by author login,
     * PR number range, and/or a regex on the PR number (as text). */
    public List<GitHubPullRequestDTO> queryPullRequests(int fromYear, int toYear, String usernamePatternText,
            Integer fromPullRequestNumber, Integer toPullRequestNumber, String pullRequestNumberPatternText) {
        return queryPullRequests(fromYear, toYear, usernamePatternText,
                fromPullRequestNumber, toPullRequestNumber, pullRequestNumberPatternText, null, null, null);
    }

    /** Lists the PRs created between fromYear and toYear (inclusive), optionally filtered by author login,
     * PR number range, a regex on the PR number (as text), the merged/mergeable tri-state flags, and/or a
     * regex on the mergeable state (as text). */
    public List<GitHubPullRequestDTO> queryPullRequests(int fromYear, int toYear, String usernamePatternText,
            Integer fromPullRequestNumber, Integer toPullRequestNumber, String pullRequestNumberPatternText,
            Boolean merged, Boolean mergeable, String mergeableStatePatternText) {
        List<GitHubPullRequestDTO> result = new ArrayList<>();
        Pattern usernamePattern = (usernamePatternText != null && !usernamePatternText.isBlank())
                ? Pattern.compile(usernamePatternText) : null;
        Pattern pullRequestNumberPattern = (pullRequestNumberPatternText != null && !pullRequestNumberPatternText.isBlank())
                ? Pattern.compile(pullRequestNumberPatternText) : null;
        Pattern mergeableStatePattern = (mergeableStatePatternText != null && !mergeableStatePatternText.isBlank())
                ? Pattern.compile(mergeableStatePatternText) : null;
        repository.scanPullRequests(fromYear, toYear, (year, pr) -> {
            boolean matches = (usernamePattern == null || usernamePattern.matcher(authorOf(pr)).matches())
                    && (fromPullRequestNumber == null || pr.number >= fromPullRequestNumber)
                    && (toPullRequestNumber == null || pr.number <= toPullRequestNumber)
                    && (pullRequestNumberPattern == null || pullRequestNumberPattern.matcher(String.valueOf(pr.number)).matches())
                    && (merged == null || merged.booleanValue() == pr.merged)
                    && (mergeable == null || mergeable.equals(pr.mergeable))
                    && (mergeableStatePattern == null || mergeableStatePattern.matcher(pr.mergeableState != null ? pr.mergeableState : "").matches());
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

    /** Lists the annotations of PRs created between fromYear and toYear (inclusive), skipping un-annotated ones. */
    public List<GitHubPullRequestAnnotationDTO> listPullRequestAnnotations(int fromYear, int toYear) {
        List<GitHubPullRequestAnnotationDTO> res = new ArrayList<>();
        repository.scanPullRequests(fromYear, toYear, (year, pr) -> {
            GitHubPullRequestExtraFieldsDTO annotated = pr.annotated;
            if (annotated != null) {
                res.add(new GitHubPullRequestAnnotationDTO(pr.number, annotated));
            }
        });
        return res;
    }

    public void putAnnotation(int number, GitHubPullRequestExtraFieldsDTO annotated) {
        repository.putAnnotation(number, annotated);
    }

    public void putPersonalInterrestComment(int number, String personalInterrestComment, Integer personalInterrestPriority10) {
        GitHubPullRequestDTO pr = repository.getByNumber(number);
        GitHubPullRequestExtraFieldsDTO annotated = pr.annotatedOrCreate();
        annotated.personalInterrestComment = personalInterrestComment;
        annotated.personalInterrestPriority10 = personalInterrestPriority10;
        repository.putAnnotation(number, annotated);
    }

    public void removeAnnotation(int number) {
        repository.removeAnnotation(number);
    }
}
