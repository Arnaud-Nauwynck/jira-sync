package fr.an.projectanalysis.github.service;

import fr.an.projectanalysis.github.repository.GitHubPullRequestRepository;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrCriteriaDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrPartitionStatsDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrQueryDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestAnnotationDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestExtraFieldsDTO;
import fr.an.projectanalysis.github.rest.dtos.NearbyGitHubPullRequestsDTO;
import fr.an.projectanalysis.github.rest.dtos.UserGitHubPullRequestStatsDTO;
import fr.an.projectanalysis.github.rest.dtos.YearCountDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class GitHubPullRequestService {

    private static final String UNKNOWN_USER = "unknown";

    private static final int DEFAULT_LIMIT = 1000;

    private static final String OPEN_STATE = "open";

    private final GitHubPullRequestRepository repository;

    public GitHubPullRequestService(GitHubPullRequestRepository repository) {
        this.repository = repository;
    }

    /** Finds a single PR by its number, or returns null if not found. */
    public GitHubPullRequestDTO findByNumber(int number) {
        return repository.findByNumber(number);
    }

    /**
     * For the PR with the given number, finds the nearest earlier ("prev") and later ("next") PR,
     * ordered by PR number, matching each of 3 independent criteria: still open, still open and
     * created by the same author, and created by the same author (regardless of state).
     */
    public NearbyGitHubPullRequestsDTO findNearbyPullRequests(int number) {
        GitHubPullRequestDTO target = repository.getByNumber(number);
        String author = authorOf(target);

        List<GitHubPullRequestDTO> allPrs = repository.findAll().stream()
                .sorted(Comparator.comparingInt(pr -> pr.number))
                .collect(Collectors.toList());

        int targetIndex = -1;
        for (int i = 0; i < allPrs.size(); i++) {
            if (allPrs.get(i).number == number) {
                targetIndex = i;
                break;
            }
        }

        NearbyGitHubPullRequestsDTO dto = new NearbyGitHubPullRequestsDTO();
        if (targetIndex < 0) {
            return dto;
        }
        for (int i = targetIndex - 1; i >= 0; i--) {
            GitHubPullRequestDTO pr = allPrs.get(i);
            boolean open = isStillOpen(pr);
            boolean sameAuthor = author.equalsIgnoreCase(authorOf(pr));
            if (dto.prevStillOpen == null && open) {
                dto.prevStillOpen = pr.number;
            }
            if (dto.prevStillOpenCreatedBySameAuthor == null && open && sameAuthor) {
                dto.prevStillOpenCreatedBySameAuthor = pr.number;
            }
            if (dto.prevCreatedBySameAuthor == null && sameAuthor) {
                dto.prevCreatedBySameAuthor = pr.number;
            }
            if (dto.prevStillOpen != null && dto.prevStillOpenCreatedBySameAuthor != null && dto.prevCreatedBySameAuthor != null) {
                break;
            }
        }
        for (int i = targetIndex + 1; i < allPrs.size(); i++) {
            GitHubPullRequestDTO pr = allPrs.get(i);
            boolean open = isStillOpen(pr);
            boolean sameAuthor = author.equalsIgnoreCase(authorOf(pr));
            if (dto.nextStillOpen == null && open) {
                dto.nextStillOpen = pr.number;
            }
            if (dto.nextStillOpenCreatedBySameAuthor == null && open && sameAuthor) {
                dto.nextStillOpenCreatedBySameAuthor = pr.number;
            }
            if (dto.nextCreatedBySameAuthor == null && sameAuthor) {
                dto.nextCreatedBySameAuthor = pr.number;
            }
            if (dto.nextStillOpen != null && dto.nextStillOpenCreatedBySameAuthor != null && dto.nextCreatedBySameAuthor != null) {
                break;
            }
        }
        return dto;
    }

    private static boolean isStillOpen(GitHubPullRequestDTO pr) {
        return OPEN_STATE.equalsIgnoreCase(pr.state);
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
        Pattern usernamePattern = compilePattern(usernamePatternText);
        Pattern pullRequestNumberPattern = compilePattern(pullRequestNumberPatternText);
        Pattern mergeableStatePattern = compilePattern(mergeableStatePatternText);
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

    /** Lists the PRs matching the given criteria (Data Fetching + Main/Analysis/Development Work/Personal
     * Interest filter criteria of the github-pull-requests page), capped at {@code query.limit} (default 1000). */
    public List<GitHubPullRequestDTO> queryPullRequests(GitHubPrQueryDTO query) {
        return queryPullRequestsMatching(query != null ? query.criteria : null, limitOf(query));
    }

    /** Same as {@link #queryPullRequests(GitHubPrQueryDTO)}, but returns only the PR numbers. */
    public List<Integer> queryPullRequestIds(GitHubPrQueryDTO query) {
        List<GitHubPullRequestDTO> matched = queryPullRequestsMatching(query != null ? query.criteria : null, limitOf(query));
        List<Integer> ids = new ArrayList<>(matched.size());
        for (GitHubPullRequestDTO pr : matched) {
            ids.add(pr.number);
        }
        return ids;
    }

    private static int limitOf(GitHubPrQueryDTO query) {
        return (query != null && query.limit != null) ? query.limit : DEFAULT_LIMIT;
    }

    private List<GitHubPullRequestDTO> queryPullRequestsMatching(GitHubPrCriteriaDTO c, int limit) {
        int fromYear = c != null && c.fromYear != null ? c.fromYear : 2020;
        int toYear = c != null && c.toYear != null ? c.toYear : 2050;
        Pattern usernamePattern = c != null ? compilePattern(c.usernamePattern) : null;
        Pattern pullRequestNumberPattern = c != null ? compilePattern(c.pullRequestNumberPattern) : null;
        Integer fromPullRequestNumber = c != null ? c.fromPullRequestNumber : null;
        Integer toPullRequestNumber = c != null ? c.toPullRequestNumber : null;
        GitHubPrCriteria criteria = new GitHubPrCriteria(c);
        List<GitHubPullRequestDTO> result = new ArrayList<>();
        // partition pruning: scan from the most recent partition (toYear) backwards, stopping as
        // soon as the limit is reached, so older partitions are never loaded once satisfied.
        repository.scanPullRequestsFromMostRecent(fromYear, toYear, (year, pr) -> {
            boolean matches = (usernamePattern == null || usernamePattern.matcher(authorOf(pr)).matches())
                    && (fromPullRequestNumber == null || pr.number >= fromPullRequestNumber)
                    && (toPullRequestNumber == null || pr.number <= toPullRequestNumber)
                    && (pullRequestNumberPattern == null || pullRequestNumberPattern.matcher(String.valueOf(pr.number)).matches())
                    && criteria.test(pr);
            if (matches) {
                result.add(pr);
            }
            return result.size() < limit;
        });
        return result;
    }

    /** Count, and lowest/highest PR number, of locally-synced PRs per "created_year" partition. */
    public GitHubPrPartitionStatsDTO queryPartitionStats() {
        GitHubPrPartitionStatsDTO dto = new GitHubPrPartitionStatsDTO();
        List<YearCountDTO> stats = new ArrayList<>();
        for (Map.Entry<Integer, GitHubPullRequestRepository.PartitionIndexes> e : repository.partitionStats().entrySet()) {
            stats.add(e.getValue().toDTO(e.getKey()));
        }
        dto.statsPerYear = stats;
        return dto;
    }

    private static Pattern compilePattern(String patternText) {
        return (patternText != null && !patternText.isBlank()) ? Pattern.compile(patternText) : null;
    }

    private static String authorOf(GitHubPullRequestDTO pr) {
        String login = pr.authorLogin;
        return login != null && !login.isBlank() ? login : UNKNOWN_USER;
    }

    /** Count PRs created per author, for PRs created between fromYear and toYear (inclusive), optionally filtered by author login. */
    public Collection<UserGitHubPullRequestStatsDTO> queryUserPullRequestStats(
            int fromYear, int toYear, String usernamePatternText) {
        Map<String, UserGitHubPullRequestStatsDTO> tmp = new LinkedHashMap<>();
        Pattern usernamePattern = compilePattern(usernamePatternText);
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
        repository.mutateIssue(number, pr -> pr.annotated = annotated);
    }

    public void putPersonalInterrestComment(int number, String personalInterrestComment, Integer personalInterrestPriority10) {
        GitHubPullRequestDTO pr = repository.getByNumber(number);
        GitHubPullRequestExtraFieldsDTO annotated = pr.annotatedOrCreate();
        annotated.personalInterrestComment = personalInterrestComment;
        annotated.personalInterrestPriority10 = personalInterrestPriority10;
        repository.mutateIssue(number, pr1 -> pr1.annotated = annotated);
    }

    public void removeAnnotation(int number) {
        repository.mutateIssue(number, pr -> pr.annotated = null);
    }
}
