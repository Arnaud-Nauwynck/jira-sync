package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.repository.JiraIssueRepository;
import fr.an.projectanalysis.jira.rest.dtos.IssueExtraFieldsDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssueIdAndLastUpdateTimeDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesCompareIdsResultDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesPartitionStatsDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueAnnotationDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.jira.rest.dtos.NearbyJiraIssuesDTO;
import fr.an.projectanalysis.jira.rest.dtos.UserJiraIssueStatsDTO;
import fr.an.projectanalysis.jira.rest.dtos.YearCountDTO;
import fr.an.projectanalysis.rest.dtos.JiraUserActivityStatsDTO;
import fr.an.projectanalysis.util.CompareIdsUtils;
import fr.an.projectanalysis.util.CompareIdsUtils.CompareIdsResult;
import fr.an.projectanalysis.util.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class JiraIssueService {

    /** Status names (lower-case) considered "closed", when no resolutiondate is set either (mirrors {@code UserJiraIssueStatsDTO}). */
    private static final Set<String> CLOSED_STATUS_NAMES = Set.of("closed", "done", "resolved");

    private final JiraIssueRepository repository;

    public JiraIssueService(JiraIssueRepository repository) {
        this.repository = repository;
    }

    /** Counts the issues created per user, among those matching the given criteria, within its "created_year" range. */
    public Collection<UserJiraIssueStatsDTO> queryUserIssueStats(JiraIssueCriteria issueCriteria) {
        Map<String, UserJiraIssueStatsDTO> tmp = new LinkedHashMap<>();
        repository.scanIssues(issueCriteria.getFromYear(), issueCriteria.getToYear(), (year, issue) -> {
            if (!issueCriteria.test(issue)) {
                return;
            }
            String user = JiraIssueCriteria.creatorOf(issue);
            UserJiraIssueStatsDTO statPerUser = tmp.computeIfAbsent(user, UserJiraIssueStatsDTO::new);
            statPerUser.add(year, issue);
        });
        return tmp.values();
    }

    /** Adds the create/comment/update/close events of the issues created between fromYear and toYear
     * (inclusive) into {@code acc}, keyed by the user who performed each event and its month. */
    public void contributeUserActivityStats(Map<String, JiraUserActivityStatsDTO> acc, int fromYear, int toYear) {
        repository.scanIssues(fromYear, toYear, (year, issue) -> JiraIssueActivityAnalyzer.contribute(acc, issue));
    }

    /** Finds a single issue by its key, or returns null if not found. */
    public JiraIssueDTO findAnnotatedIssueByKey(String key) {
        return repository.findByKey(key);
    }

    /** Finds the issues having the given keys ("ids"), in the requested order; keys not found locally are skipped. */
    public List<JiraIssueDTO> findIssuesByIds(Collection<String> ids) {
        List<JiraIssueDTO> res = new ArrayList<>(ids.size());
        for (String id : ids) {
            JiraIssueDTO found = repository.findByKey(id);
            if (found != null) {
                res.add(found);
            }
        }
        return res;
    }

    /**
     * For the issue with the given key, finds the nearest earlier ("prev") and later ("next")
     * issue, ordered by issue number within the same Jira project, matching each of 3 independent
     * criteria: still open, still open and created by the same author, and created by the same
     * author (regardless of status). Walks the "created_year" partitions one at a time, starting
     * at the issue's own partition and expanding outward, so only the partitions actually needed
     * to resolve all 3 criteria on each side are loaded.
     */
    public NearbyJiraIssuesDTO findNearbyIssues(String key) {
        JiraIssueDTO target = repository.getByKey(key);
        String projectPrefix = projectPrefixOf(key);
        String author = JiraIssueCriteria.creatorOf(target);
        int targetYear = JiraIssueRepository.partitionYearOf(target);

        List<Integer> years = repository.findAllPartitionYears();
        int targetYearIdx = years.indexOf(targetYear);

        NearbyJiraIssuesDTO dto = new NearbyJiraIssuesDTO();
        if (targetYearIdx < 0) {
            return dto;
        }

        prevLoop:
        for (int yi = targetYearIdx; yi >= 0; yi--) {
            List<JiraIssueDTO> issues = sameProjectIssuesInPartitionSorted(years.get(yi), projectPrefix);
            int fromIndex = issues.size() - 1;
            if (yi == targetYearIdx) {
                int targetIndex = indexOfKey(issues, key);
                if (targetIndex < 0) {
                    continue;
                }
                fromIndex = targetIndex - 1;
            }
            for (int i = fromIndex; i >= 0; i--) {
                JiraIssueDTO issue = issues.get(i);
                boolean open = isStillOpen(issue);
                boolean sameAuthor = author.equalsIgnoreCase(JiraIssueCriteria.creatorOf(issue));
                if (dto.prevStillOpen == null && open) {
                    dto.prevStillOpen = issue.key;
                }
                if (dto.prevStillOpenCreatedBySameAuthor == null && open && sameAuthor) {
                    dto.prevStillOpenCreatedBySameAuthor = issue.key;
                }
                if (dto.prevCreatedBySameAuthor == null && sameAuthor) {
                    dto.prevCreatedBySameAuthor = issue.key;
                }
                if (dto.prevStillOpen != null && dto.prevStillOpenCreatedBySameAuthor != null && dto.prevCreatedBySameAuthor != null) {
                    break prevLoop;
                }
            }
        }

        nextLoop:
        for (int yi = targetYearIdx; yi < years.size(); yi++) {
            List<JiraIssueDTO> issues = sameProjectIssuesInPartitionSorted(years.get(yi), projectPrefix);
            int fromIndex = 0;
            if (yi == targetYearIdx) {
                int targetIndex = indexOfKey(issues, key);
                if (targetIndex < 0) {
                    continue;
                }
                fromIndex = targetIndex + 1;
            }
            for (int i = fromIndex; i < issues.size(); i++) {
                JiraIssueDTO issue = issues.get(i);
                boolean open = isStillOpen(issue);
                boolean sameAuthor = author.equalsIgnoreCase(JiraIssueCriteria.creatorOf(issue));
                if (dto.nextStillOpen == null && open) {
                    dto.nextStillOpen = issue.key;
                }
                if (dto.nextStillOpenCreatedBySameAuthor == null && open && sameAuthor) {
                    dto.nextStillOpenCreatedBySameAuthor = issue.key;
                }
                if (dto.nextCreatedBySameAuthor == null && sameAuthor) {
                    dto.nextCreatedBySameAuthor = issue.key;
                }
                if (dto.nextStillOpen != null && dto.nextStillOpenCreatedBySameAuthor != null && dto.nextCreatedBySameAuthor != null) {
                    break nextLoop;
                }
            }
        }
        return dto;
    }

    /** The issues of a single "created_year" partition belonging to the given project, ordered by issue number. */
    private List<JiraIssueDTO> sameProjectIssuesInPartitionSorted(int year, String projectPrefix) {
        List<JiraIssueDTO> result = new ArrayList<>();
        repository.scanIssues(year, year, (y, issue) -> {
            if (projectPrefix.equals(projectPrefixOf(issue.key))) {
                result.add(issue);
            }
        });
        result.sort(Comparator.comparing(issue -> JiraIssueCriteria.issueNumberOf(issue.key), Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private static int indexOfKey(List<JiraIssueDTO> issues, String key) {
        for (int i = 0; i < issues.size(); i++) {
            if (key.equals(issues.get(i).key)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isStillOpen(JiraIssueDTO issue) {
        JiraIssueDTO.IssueFieldsDTO f = issue.fields;
        String status = f != null ? f.status : null;
        String resolutiondate = f != null ? f.resolutiondate : null;
        boolean closed = (resolutiondate != null && !resolutiondate.isBlank())
                || (status != null && CLOSED_STATUS_NAMES.contains(status.toLowerCase(Locale.ROOT)));
        return !closed;
    }

    /** The Jira project prefix of a key (eg "PROJ" in "PROJ-123"). */
    private static String projectPrefixOf(String key) {
        if (key == null) {
            return "";
        }
        int dashIdx = key.lastIndexOf('-');
        return dashIdx >= 0 ? key.substring(0, dashIdx) : key;
    }

    /** Lists the issues created between fromYear and toYear (inclusive), optionally filtered by creator username. */
    public List<JiraIssueDTO> queryAnnotatedIssues(int fromYear, int toYear, String usernamePatternText) {
        List<JiraIssueDTO> result = new ArrayList<>();
        JiraIssueCriteria issueCriteria = JiraIssueCriteria.ofUsernamePattern(usernamePatternText);
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            if (issueCriteria.test(issue)) {
                result.add(issue);
            }
        });
        return result;
    }

    /** Lists the issues matching the given criteria (Data Fetching + Main/Analysis/Development Work/Personal
     * Interest filter criteria of the issues-list page), capped at {@code limit}. */
    public List<JiraIssueDTO> queryIssues(JiraIssueCriteria issueCriteria, int limit) {
        List<JiraIssueDTO> result = new ArrayList<>();
        // partition pruning: scan from the most recent partition (toYear) backwards, stopping as
        // soon as the limit is reached, so older partitions are never loaded once satisfied.
        repository.scanIssuesFromMostRecent(issueCriteria.getFromYear(), issueCriteria.getToYear(), (year, issue) -> {
            if (issueCriteria.test(issue)) {
                result.add(issue);
            }
            return result.size() < limit;
        });
        return result;
    }

    /** Same as {@link #queryIssues(JiraIssueCriteria, int)}, but returns only the issue keys. */
    public List<String> queryIssueIds(JiraIssueCriteria issueCriteria, int limit) {
        List<JiraIssueDTO> matched = queryIssues(issueCriteria, limit);
        List<String> ids = new ArrayList<>(matched.size());
        for (JiraIssueDTO issue : matched) {
            ids.add(issue.key);
        }
        return ids;
    }

    /** Same as {@link #queryIssueIds(JiraIssueCriteria, int)}, but returns for each issue its key with its
     * last update time, in epoch milliseconds. */
    public List<IssueIdAndLastUpdateTimeDTO> queryIssueIdAndLastUpdateTimes(JiraIssueCriteria issueCriteria, int limit) {
        List<JiraIssueDTO> matched = queryIssues(issueCriteria, limit);
        List<IssueIdAndLastUpdateTimeDTO> res = new ArrayList<>(matched.size());
        for (JiraIssueDTO issue : matched) {
            String updated = (issue.fields != null) ? issue.fields.updated : null;
            res.add(new IssueIdAndLastUpdateTimeDTO(issue.key, DateTimeUtils.toEpochMillisOr0(updated)));
        }
        return res;
    }

    /**
     * Compares the issue ids matched by 2 independent criteria: the ids matched by the left criteria
     * only, by both ("common"), and by the right criteria only. The common ids are only counted,
     * unless {@code fillCommonIds} is set, in which case they are also listed. Each side is capped
     * at {@code limit}, as in {@link #queryIssueIds(JiraIssueCriteria, int)}.
     */
    public IssuesCompareIdsResultDTO compareQueryIds(
            JiraIssueCriteria leftCriteria, int leftLimit,
            JiraIssueCriteria rightCriteria, int rightLimit,
            boolean fillCommonIds) {
        List<String> leftIds = queryIssueIds(leftCriteria, leftLimit);
        List<String> rightIds = queryIssueIds(rightCriteria, rightLimit);
        CompareIdsResult<String> compared = CompareIdsUtils.compareIds(leftIds, rightIds, fillCommonIds);

        IssuesCompareIdsResultDTO res = new IssuesCompareIdsResultDTO();
        res.leftOnlyIds = compared.leftOnlyIds;
        res.commonIds = compared.commonIds;
        res.commonCount = compared.commonCount;
        res.rightOnlyIds = compared.rightOnlyIds;
        return res;
    }

    /** Count, and lowest/highest issue number, of locally-synced issues per "created_year" partition. */
    public IssuesPartitionStatsDTO queryPartitionStats() {
        IssuesPartitionStatsDTO dto = new IssuesPartitionStatsDTO();
        List<YearCountDTO> stats = new ArrayList<>();
        for (Map.Entry<Integer, JiraIssueRepository.PartitionIndexes> e : repository.partitionStats().entrySet()) {
            stats.add(e.getValue().toDTO(e.getKey()));
        }
        dto.statsPerYear = stats;
        return dto;
    }

    public List<JiraIssueAnnotationDTO> listIssueAnnotations(int fromYear, int toYear) {
        val res = new ArrayList<JiraIssueAnnotationDTO>();
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            val annotation = issue.getAnnotated();
            if (annotation != null) {
                res.add(new JiraIssueAnnotationDTO(issue.key, annotation));
            }
        });
        return res;
    }

    public JiraIssueDTO getByKey(String key) {
        return repository.getByKey(key);
    }

    public void putAnnotation(String key, IssueExtraFieldsDTO annotated) {
        repository.putAnnotation(key, annotated);
    }

    public void putPersonalInterrestComment(String key, String personalInterrestComment, Integer personalInterrestPriority10) {
        JiraIssueDTO issue = repository.getByKey(key);
        IssueExtraFieldsDTO annotated = issue.getAnnotated();
        if (annotated == null) {
            annotated = new IssueExtraFieldsDTO();
        }
        annotated.setPersonalInterrestComment(personalInterrestComment);
        annotated.setPersonalInterrestPriority10(personalInterrestPriority10);
        repository.putAnnotation(key, annotated);
    }

    public void removeAnnotation(String key) {
        repository.removeAnnotation(key);
    }

}
