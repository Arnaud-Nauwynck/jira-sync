package fr.an.projectanalysis.github.service;

import fr.an.projectanalysis.github.repository.GitHubPullRequestRepository;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrCriteriaDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrPartitionStatsDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPrQueryDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestAnnotationDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestDTO;
import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestExtraFieldsDTO;
import fr.an.projectanalysis.github.rest.dtos.UserGitHubPullRequestStatsDTO;
import fr.an.projectanalysis.github.rest.dtos.YearCountDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class GitHubPullRequestService {

    private static final String UNKNOWN_USER = "unknown";

    private static final int DEFAULT_LIMIT = 1000;

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
        List<GitHubPullRequestDTO> result = new ArrayList<>();
        repository.scanPullRequests(fromYear, toYear, (year, pr) -> {
            if (result.size() >= limit) {
                return;
            }
            boolean matches = (usernamePattern == null || usernamePattern.matcher(authorOf(pr)).matches())
                    && (fromPullRequestNumber == null || pr.number >= fromPullRequestNumber)
                    && (toPullRequestNumber == null || pr.number <= toPullRequestNumber)
                    && (pullRequestNumberPattern == null || pullRequestNumberPattern.matcher(String.valueOf(pr.number)).matches())
                    && matchesCriteria(c, pr);
            if (matches) {
                result.add(pr);
            }
        });
        return result;
    }

    private static boolean matchesCriteria(GitHubPrCriteriaDTO c, GitHubPullRequestDTO pr) {
        if (c == null) {
            return true;
        }
        if (!matchesAny(c.titleContains, pr.title)) {
            return false;
        }
        if (!matchesAny(c.bodyContains, pr.body)) {
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
        if (!matchesAny(c.authorContains, authorValues.toArray(String[]::new))) {
            return false;
        }
        List<String> labels = pr.labelNames != null ? pr.labelNames : List.of();
        if (!matchesAny(c.labelContains, labels.toArray(String[]::new))) {
            return false;
        }
        if (!matchesAny(c.baseRefContains, pr.baseRef)) {
            return false;
        }
        if (isExcluded(c.excludedStates, pr.state)) {
            return false;
        }
        if (!matchesAvailability(c.draftAvailability, pr.draft)) {
            return false;
        }
        if (!matchesAvailability(c.mergedAvailability, pr.merged)) {
            return false;
        }
        if (!matchesAvailability(c.mergeableAvailability, Boolean.TRUE.equals(pr.mergeable))) {
            return false;
        }
        if (!matchesRegex(c.mergeableStatePattern, pr.mergeableState)) {
            return false;
        }

        GitHubPullRequestExtraFieldsDTO annotated = pr.annotated;
        boolean hasAnalysis = annotated != null && annotated.analysisSummary != null && !annotated.analysisSummary.isBlank();
        if (!matchesAvailability(c.analysisAvailability, hasAnalysis)) {
            return false;
        }
        if (!matchesAny(c.analysisSummaryContains, annotated != null ? annotated.analysisSummary : null)) {
            return false;
        }
        if (!matchesDateRange(c.analysisSummaryUpdatedFrom, c.analysisSummaryUpdatedTo,
                annotated != null ? annotated.analysisSummaryLastUpdateTime : null)) {
            return false;
        }
        if (!matchesTokensRangeK(c.analysisSummaryMinTokensK, c.analysisSummaryMaxTokensK,
                annotated != null ? annotated.analysisSummaryTokensConsumed : 0)) {
            return false;
        }
        List<String> analysisExtraPrompts = annotated != null && annotated.analysisUserExtraPrompts != null
                ? annotated.analysisUserExtraPrompts : List.of();
        if (!matchesAny(c.analysisUserExtraPromptsContains, analysisExtraPrompts.toArray(String[]::new))) {
            return false;
        }

        boolean hasDevWork = annotated != null && annotated.developmentWorkDescribed != null && !annotated.developmentWorkDescribed.isBlank();
        if (!matchesAvailability(c.developmentWorkAvailability, hasDevWork)) {
            return false;
        }
        if (!matchesAny(c.developmentWorkDescribedContains, annotated != null ? annotated.developmentWorkDescribed : null)) {
            return false;
        }
        if (!matchesDateRange(c.developmentWorkUpdatedFrom, c.developmentWorkUpdatedTo,
                annotated != null ? annotated.developmentWorkLastUpdateTime : null)) {
            return false;
        }
        if (!matchesTokensRangeK(c.developmentWorkMinTokensK, c.developmentWorkMaxTokensK,
                annotated != null ? annotated.developmentWorkTokensConsumed : 0)) {
            return false;
        }
        List<String> devWorkExtraPrompts = annotated != null && annotated.developmentWorkUserExtraPrompts != null
                ? annotated.developmentWorkUserExtraPrompts : List.of();
        if (!matchesAny(c.developmentWorkUserExtraPromptsContains, devWorkExtraPrompts.toArray(String[]::new))) {
            return false;
        }

        boolean hasPersonalInterrest = annotated != null && annotated.personalInterrestComment != null && !annotated.personalInterrestComment.isBlank();
        if (!matchesAvailability(c.personalInterrestAvailability, hasPersonalInterrest)) {
            return false;
        }
        if (!matchesAny(c.personalInterrestCommentContains, annotated != null ? annotated.personalInterrestComment : null)) {
            return false;
        }
        if (!matchesNumberRange(c.personalInterrestMinPriority, c.personalInterrestMaxPriority,
                annotated != null ? annotated.personalInterrestPriority10 : null)) {
            return false;
        }
        return true;
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

    /** Whether the (bucketed) value is in the comma-separated excluded list. */
    private static boolean isExcluded(String excludedCsv, String value) {
        return parseCsvList(excludedCsv).contains(value);
    }

    /** True when the CSV filter is blank, or at least one of the given values contains (case-insensitively) one of its comma-separated terms. */
    private static boolean matchesAny(String csvFilter, String... values) {
        List<String> terms = parseCsvList(csvFilter);
        if (terms.isEmpty()) {
            return true;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String lower = value.toLowerCase();
            for (String term : terms) {
                if (lower.contains(term.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> parseCsvList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** Full-match regex test; a blank pattern always matches, and a null value fails unless the pattern is blank. */
    private static boolean matchesRegex(String patternText, String value) {
        Pattern pattern = compilePattern(patternText);
        if (pattern == null) {
            return true;
        }
        return value != null && pattern.matcher(value).matches();
    }

    /** 'yes' requires present, 'no' requires absent, 'any'/blank/null does not filter. */
    private static boolean matchesAvailability(String availability, boolean present) {
        if ("yes".equals(availability)) {
            return present;
        }
        if ("no".equals(availability)) {
            return !present;
        }
        return true;
    }

    private static boolean matchesDateRange(String fromDate, String toDate, LocalDateTime value) {
        boolean hasFrom = fromDate != null && !fromDate.isBlank();
        boolean hasTo = toDate != null && !toDate.isBlank();
        if (!hasFrom && !hasTo) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (hasFrom && value.isBefore(LocalDate.parse(fromDate).atStartOfDay())) {
            return false;
        }
        if (hasTo && value.isAfter(LocalDate.parse(toDate).atTime(23, 59, 59))) {
            return false;
        }
        return true;
    }

    private static boolean matchesNumberRange(Integer min, Integer max, Integer value) {
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (min != null && value < min) {
            return false;
        }
        if (max != null && value > max) {
            return false;
        }
        return true;
    }

    /** min/max are expressed in kilo-tokens (thousands); value is the raw token count. */
    private static boolean matchesTokensRangeK(Integer minK, Integer maxK, int value) {
        if (minK == null && maxK == null) {
            return true;
        }
        if (minK != null && value < minK * 1000) {
            return false;
        }
        if (maxK != null && value > maxK * 1000) {
            return false;
        }
        return true;
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
