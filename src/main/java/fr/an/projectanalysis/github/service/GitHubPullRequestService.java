package fr.an.projectanalysis.github.service;

import fr.an.projectanalysis.github.repository.GitHubPullRequestRepository;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrCompareIdsResultDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrIdAndLastUpdateTimeDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrPartitionStatsDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestAnnotationDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestExtraFieldsDTO;
import fr.an.projectanalysis.github.rest.dtos.NearbyGitHubPullRequestsDTO;
import fr.an.projectanalysis.github.rest.dtos.UserGitHubPullRequestStatsDTO;
import fr.an.projectanalysis.github.rest.dtos.YearCountDTO;
import fr.an.projectanalysis.rest.dtos.UserActivityStatsDTO;
import fr.an.projectanalysis.util.CompareIdsUtils;
import fr.an.projectanalysis.util.CompareIdsUtils.CompareIdsResult;
import fr.an.projectanalysis.util.CritUtils;
import fr.an.projectanalysis.util.DateTimeUtils;
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

    private static final String OPEN_STATE = "open";

    private final GitHubPullRequestRepository repository;

    public GitHubPullRequestService(GitHubPullRequestRepository repository) {
        this.repository = repository;
    }

    /** Finds a single PR by its number, or returns null if not found. */
    public GitHubPullRequestDTO findByNumber(int number) {
        return repository.findByNumber(number);
    }

    /** Finds the pull requests having the given numbers ("ids"), in the requested order; numbers not found
     * locally are skipped. */
    public List<GitHubPullRequestDTO> findByIds(Collection<Integer> ids) {
        List<GitHubPullRequestDTO> res = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            GitHubPullRequestDTO found = repository.findByNumber(id);
            if (found != null) {
                res.add(found);
            }
        }
        return res;
    }

    /**
     * For the PR with the given number, finds the nearest earlier ("prev") and later ("next") PR,
     * ordered by PR number, matching each of 3 independent criteria: still open, still open and
     * created by the same author, and created by the same author (regardless of state).
     */
    public NearbyGitHubPullRequestsDTO findNearbyPullRequests(int number) {
        GitHubPullRequestDTO target = repository.getByNumber(number);
        String author = GitHubPrCriteria.authorOf(target);

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
            boolean sameAuthor = author.equalsIgnoreCase(GitHubPrCriteria.authorOf(pr));
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
            boolean sameAuthor = author.equalsIgnoreCase(GitHubPrCriteria.authorOf(pr));
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
        List<GitHubPullRequestDTO> result = new ArrayList<>();
        GitHubPrCriteria criteria = GitHubPrCriteria.ofUsernamePattern(usernamePatternText);
        repository.scanPullRequests(fromYear, toYear, (year, pr) -> {
            if (criteria.test(pr)) {
                result.add(pr);
            }
        });
        return result;
    }

    /** Lists the PRs matching the given criteria (Data Fetching + Main/Analysis/Development Work/Personal
     * Interest filter criteria of the github-pull-requests page), capped at {@code limit}. */
    public List<GitHubPullRequestDTO> queryPullRequests(GitHubPrCriteria criteria, int limit) {
        List<GitHubPullRequestDTO> result = new ArrayList<>();
        // partition pruning: scan from the most recent partition (toYear) backwards, stopping as
        // soon as the limit is reached, so older partitions are never loaded once satisfied.
        repository.scanPullRequestsFromMostRecent(criteria.getFromYear(), criteria.getToYear(), (year, pr) -> {
            if (criteria.test(pr)) {
                result.add(pr);
            }
            return result.size() < limit;
        });
        return result;
    }

    /** Same as {@link #queryPullRequests(GitHubPrCriteria, int)}, but returns only the PR numbers. */
    public List<Integer> queryPullRequestIds(GitHubPrCriteria criteria, int limit) {
        List<GitHubPullRequestDTO> matched = queryPullRequests(criteria, limit);
        List<Integer> ids = new ArrayList<>(matched.size());
        for (GitHubPullRequestDTO pr : matched) {
            ids.add(pr.number);
        }
        return ids;
    }

    /** Same as {@link #queryPullRequestIds(GitHubPrCriteria, int)}, but returns for each pull request its
     * number with its last update time, in epoch milliseconds. */
    public List<GitHubPrIdAndLastUpdateTimeDTO> queryPullRequestIdAndLastUpdateTimes(GitHubPrCriteria criteria, int limit) {
        List<GitHubPullRequestDTO> matched = queryPullRequests(criteria, limit);
        List<GitHubPrIdAndLastUpdateTimeDTO> res = new ArrayList<>(matched.size());
        for (GitHubPullRequestDTO pr : matched) {
            res.add(new GitHubPrIdAndLastUpdateTimeDTO(pr.number, DateTimeUtils.toEpochMillisOr0(pr.updatedAt)));
        }
        return res;
    }

    /**
     * Compares the PR numbers matched by 2 independent criteria: the numbers matched by the left criteria
     * only, by both ("common"), and by the right criteria only. The common ids are only counted, unless
     * {@code fillCommonIds} is set, in which case they are also listed. Each side is capped at its own
     * limit, as in {@link #queryPullRequestIds(GitHubPrCriteria, int)}.
     */
    public GitHubPrCompareIdsResultDTO compareQueryIds(
            GitHubPrCriteria leftCriteria, int leftLimit,
            GitHubPrCriteria rightCriteria, int rightLimit,
            boolean fillCommonIds) {
        List<Integer> leftIds = queryPullRequestIds(leftCriteria, leftLimit);
        List<Integer> rightIds = queryPullRequestIds(rightCriteria, rightLimit);
        CompareIdsResult<Integer> compared = CompareIdsUtils.compareIds(leftIds, rightIds, fillCommonIds);

        GitHubPrCompareIdsResultDTO res = new GitHubPrCompareIdsResultDTO();
        res.leftOnlyIds = compared.leftOnlyIds;
        res.commonIds = compared.commonIds;
        res.commonCount = compared.commonCount;
        res.rightOnlyIds = compared.rightOnlyIds;
        return res;
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

    /** Count PRs created per author, for PRs created between fromYear and toYear (inclusive), optionally filtered by author login. */
    public Collection<UserGitHubPullRequestStatsDTO> queryUserPullRequestStats(
            int fromYear, int toYear, String usernamePatternText) {
        Map<String, UserGitHubPullRequestStatsDTO> tmp = new LinkedHashMap<>();
        Pattern usernamePattern = CritUtils.compilePattern(usernamePatternText);
        repository.scanPullRequests(fromYear, toYear, (year, pr) -> {
            String user = GitHubPrCriteria.authorOf(pr);
            if (!CritUtils.matchesRegex(usernamePattern, user)) {
                return;
            }
            UserGitHubPullRequestStatsDTO statPerUser = tmp.computeIfAbsent(user, UserGitHubPullRequestStatsDTO::new);
            statPerUser.add(year, pr);
        });
        return tmp.values();
    }

    /** Adds the create/comment/update/merge/close events of the PRs created between fromYear and toYear
     * (inclusive) into {@code acc}, keyed by the user who performed each event and its month. */
    public void contributeUserActivityStats(Map<String, UserActivityStatsDTO> acc, int fromYear, int toYear) {
        repository.scanPullRequests(fromYear, toYear, (year, pr) -> GitHubPullRequestActivityAnalyzer.contribute(acc, pr));
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
