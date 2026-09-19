package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.repository.JiraIssueRepository;
import fr.an.projectanalysis.jira.rest.dtos.IssueExtraFieldsDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesCriteriaDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesPartitionStatsDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesQueryDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueAnnotationDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.jira.rest.dtos.NearbyJiraIssuesDTO;
import fr.an.projectanalysis.jira.rest.dtos.UserJiraIssueStatsDTO;
import fr.an.projectanalysis.jira.rest.dtos.YearCountDTO;
import fr.an.projectanalysis.util.CritUtils;
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
import java.util.regex.Pattern;

@Component
@Slf4j
public class JiraIssueService {

    /** Status names (lower-case) considered "closed", when no resolutiondate is set either (mirrors {@code UserJiraIssueStatsDTO}). */
    private static final Set<String> CLOSED_STATUS_NAMES = Set.of("closed", "done", "resolved");

    private final JiraIssueRepository repository;

    public JiraIssueService(JiraIssueRepository repository) {
        this.repository = repository;
    }

    public Collection<UserJiraIssueStatsDTO> queryUserIssueStats(
            int fromYear, int toYear,
            String usernamePatternText,
            String summaryPatternText,
            String descriptionPatternText,
            String commentPatternText,
            String commentAuthorPatternText
    ) {
        Map<String, UserJiraIssueStatsDTO> tmp = new LinkedHashMap<>();
        Pattern usernamePattern = CritUtils.compilePattern(usernamePatternText);
        Pattern summaryPattern = CritUtils.compilePattern(summaryPatternText);
        Pattern descriptionPattern = CritUtils.compilePattern(descriptionPatternText);
        Pattern commentPattern = CritUtils.compilePattern(commentPatternText);
        Pattern commentAuthorPattern = CritUtils.compilePattern(commentAuthorPatternText);
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            String user = JiraIssueCriteria.creatorOf(issue);
            if (!CritUtils.matchesRegex(usernamePattern, user)) {
                return;
            }
            if (!CritUtils.findsRegex(summaryPattern, issue.fields != null ? issue.fields.summary : null)) {
                return;
            }
            if (!CritUtils.findsRegex(descriptionPattern, issue.fields != null ? issue.fields.description : null)) {
                return;
            }
            if (!matchesComments(commentPattern, commentAuthorPattern, issue)) {
                return;
            }
            UserJiraIssueStatsDTO statPerUser = tmp.computeIfAbsent(user, UserJiraIssueStatsDTO::new);
            statPerUser.add(year, issue);
        });
        return tmp.values();
    }

    /** True when neither pattern is set, or the issue has at least one comment matching both given patterns. */
    private static boolean matchesComments(Pattern commentPattern, Pattern commentAuthorPattern, JiraIssueDTO issue) {
        if (commentPattern == null && commentAuthorPattern == null) {
            return true;
        }
        List<JiraIssueDTO.IssueCommentDTO> comments = issue.fields != null ? issue.fields.comments : null;
        if (comments == null) {
            return false;
        }
        for (JiraIssueDTO.IssueCommentDTO comment : comments) {
            if (CritUtils.findsRegex(commentPattern, comment.body)
                    && CritUtils.matchesRegex(commentAuthorPattern, comment.author)) {
                return true;
            }
        }
        return false;
    }

    /** Finds a single issue by its key, or returns null if not found. */
    public JiraIssueDTO findAnnotatedIssueByKey(String key) {
        return repository.findByKey(key);
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
        IssuesCriteriaDTO c = new IssuesCriteriaDTO();
        c.usernamePattern = usernamePatternText;
        JiraIssueCriteria issueCriteria = new JiraIssueCriteria(c);
        List<JiraIssueDTO> result = new ArrayList<>();
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            if (issueCriteria.test(issue)) {
                result.add(issue);
            }
        });
        return result;
    }

    private static final int DEFAULT_LIMIT = 1000;

    /** Lists the issues matching the given criteria (Data Fetching + Main/Analysis/Development Work/Personal
     * Interest filter criteria of the issues-list page), capped at {@code query.limit} (default 1000). */
    public List<JiraIssueDTO> queryIssues(IssuesQueryDTO query) {
        return queryIssuesMatching(query != null ? query.criteria : null, limitOf(query));
    }

    /** Same as {@link #queryIssues(IssuesQueryDTO)}, but returns only the issue keys. */
    public List<String> queryIssueIds(IssuesQueryDTO query) {
        List<JiraIssueDTO> matched = queryIssuesMatching(query != null ? query.criteria : null, limitOf(query));
        List<String> ids = new ArrayList<>(matched.size());
        for (JiraIssueDTO issue : matched) {
            ids.add(issue.key);
        }
        return ids;
    }

    private static int limitOf(IssuesQueryDTO query) {
        return (query != null && query.limit != null) ? query.limit : DEFAULT_LIMIT;
    }

    private List<JiraIssueDTO> queryIssuesMatching(IssuesCriteriaDTO c, int limit) {
        int fromYear = c != null && c.fromYear != null ? c.fromYear : 2020;
        int toYear = c != null && c.toYear != null ? c.toYear : 2050;
        JiraIssueCriteria issueCriteria = new JiraIssueCriteria(c);
        List<JiraIssueDTO> result = new ArrayList<>();
        // partition pruning: scan from the most recent partition (toYear) backwards, stopping as
        // soon as the limit is reached, so older partitions are never loaded once satisfied.
        repository.scanIssuesFromMostRecent(fromYear, toYear, (year, issue) -> {
            if (issueCriteria.test(issue)) {
                result.add(issue);
            }
            return result.size() < limit;
        });
        return result;
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
