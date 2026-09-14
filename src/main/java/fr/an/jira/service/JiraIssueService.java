package fr.an.jira.service;

import fr.an.jira.repository.JiraIssueRepository;
import fr.an.jira.rest.dtos.IssueExtraFieldsDTO;
import fr.an.jira.rest.dtos.JiraIssueAnnotationDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO;
import fr.an.jira.rest.dtos.JiraIssueQueryCriteriaDTO;
import fr.an.jira.rest.dtos.UserJiraIssueStatsDTO;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JiraIssueService {

    private static final String UNKNOWN_USER = "unknown";

    private static final String PULL_REQUEST_AVAILABLE_LABEL = "pull-request-available";

    private static final String OTHER_TYPES = "(others)";
    private static final Set<String> KNOWN_TYPES = Set.of(
            "Bug", "Improvement", "New Feature", "Story", "Epic", "Sub-task", "Task", "Umbrella", "Question",
            "Wish", "Test", "Documentation", "IT Help", "Brainstorming", "Dependency upgrade", "Request",
            "Planned Work", "Github Integration", "RTC", "Blog - New Blog Request");

    private static final String OTHER_RESOLUTIONS = "(others)";
    private static final Set<String> KNOWN_RESOLUTIONS = Set.of(
            "Done", "Fixed", "Invalid", "Incomplete", "Cannot Reproduce", "Works for Me", "Not A Problem",
            "Won't Fix", "Won't Do", "Later", "Duplicate", "Resolved", "Not A Bug", "Abandoned", "Auto Closed",
            "WorkAround", "Workaround", "Implemented", "Information Provided", "");

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
        Pattern usernamePattern = compilePattern(usernamePatternText);
        Pattern summaryPattern = compilePattern(summaryPatternText);
        Pattern descriptionPattern = compilePattern(descriptionPatternText);
        Pattern commentPattern = compilePattern(commentPatternText);
        Pattern commentAuthorPattern = compilePattern(commentAuthorPatternText);
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            String user = creatorOf(issue);
            if (usernamePattern != null && !usernamePattern.matcher(user).matches()) {
                return;
            }
            if (!matchesText(summaryPattern, issue.fields != null ? issue.fields.summary : null)) {
                return;
            }
            if (!matchesText(descriptionPattern, issue.fields != null ? issue.fields.description : null)) {
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

    private static Pattern compilePattern(String patternText) {
        return (patternText != null && !patternText.isBlank()) ? Pattern.compile(patternText) : null;
    }

    private static boolean matchesText(Pattern pattern, String text) {
        return pattern == null || (text != null && pattern.matcher(text).find());
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
            boolean bodyMatches = commentPattern == null || (comment.body != null && commentPattern.matcher(comment.body).find());
            boolean authorMatches = commentAuthorPattern == null || (comment.author != null && commentAuthorPattern.matcher(comment.author).matches());
            if (bodyMatches && authorMatches) {
                return true;
            }
        }
        return false;
    }

    /** Finds a single issue by its key, or returns null if not found. */
    public JiraIssueDTO findAnnotatedIssueByKey(String key) {
        return repository.findByKey(key);
    }

    /** Lists the issues created between fromYear and toYear (inclusive), optionally filtered by creator username. */
    public List<JiraIssueDTO> queryAnnotatedIssues(int fromYear, int toYear, String usernamePatternText) {
        return queryAnnotatedIssues(fromYear, toYear, usernamePatternText, null, null, null);
    }

    /** Lists the issues created between fromYear and toYear (inclusive), optionally filtered by creator username,
     * issue number range (the numeric suffix of the key), and/or a regex on the full issue key. */
    public List<JiraIssueDTO> queryAnnotatedIssues(int fromYear, int toYear, String usernamePatternText,
            Integer fromNumber, Integer toNumber, String keyPatternText) {
        return queryAnnotatedIssues(fromYear, toYear, usernamePatternText, fromNumber, toNumber, keyPatternText, null);
    }

    /** Same as above, plus the Main/Analysis/Development Work/Personal Interest filter criteria from the issues-list page. */
    public List<JiraIssueDTO> queryAnnotatedIssues(int fromYear, int toYear, String usernamePatternText,
            Integer fromNumber, Integer toNumber, String keyPatternText, JiraIssueQueryCriteriaDTO criteria) {
        List<JiraIssueDTO> result = new ArrayList<>();
        Pattern usernamePattern = compilePattern(usernamePatternText);
        Pattern keyPattern = compilePattern(keyPatternText);
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            boolean matches = (usernamePattern == null || usernamePattern.matcher(creatorOf(issue)).matches())
                    && (keyPattern == null || (issue.key != null && keyPattern.matcher(issue.key).matches()))
                    && matchesNumberRange(issue.key, fromNumber, toNumber)
                    && matchesCriteria(criteria, issue);
            if (matches) {
                result.add(issue);
            }
        });
        return result;
    }

    private static boolean matchesCriteria(JiraIssueQueryCriteriaDTO c, JiraIssueDTO issue) {
        if (c == null) {
            return true;
        }
        JiraIssueDTO.IssueFieldsDTO fields = issue.fields;
        if (!matchesAny(c.summaryContains, fields != null ? fields.summary : null)) {
            return false;
        }
        if (!matchesAny(c.descriptionContains, fields != null ? fields.description : null)) {
            return false;
        }
        if (!matchesAny(c.authorContains, fields != null ? fields.creator : null, fields != null ? fields.reporter : null)) {
            return false;
        }
        List<JiraIssueDTO.IssueCommentDTO> comments = fields != null && fields.comments != null ? fields.comments : List.of();
        if (!matchesAny(c.commentsContains, comments.stream().map(cm -> cm.body).toArray(String[]::new))) {
            return false;
        }
        if (!matchesAny(c.commentAuthorContains, comments.stream().map(cm -> cm.author).toArray(String[]::new))) {
            return false;
        }
        if (isExcluded(c.excludedTypes, bucketedValue(fields != null ? fields.issuetype : null, KNOWN_TYPES, OTHER_TYPES))) {
            return false;
        }
        if (isExcluded(c.excludedResolutions, bucketedValue(fields != null ? fields.resolution : null, KNOWN_RESOLUTIONS, OTHER_RESOLUTIONS))) {
            return false;
        }
        if (isExcluded(c.excludedStatuses, fields != null ? fields.status : null)) {
            return false;
        }
        if (isExcluded(c.excludedPriorities, fields != null ? fields.priority : null)) {
            return false;
        }
        List<String> labels = fields != null && fields.labels != null ? fields.labels : List.of();
        if (!matchesAny(c.labelsContains, labels.toArray(String[]::new))) {
            return false;
        }
        if (!matchesAvailability(c.pullRequestAvailableLabel, labels.contains(PULL_REQUEST_AVAILABLE_LABEL))) {
            return false;
        }

        IssueExtraFieldsDTO annotated = issue.annotated;
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

    /** Maps a value to itself if it is a known enum option, or to the "others" bucket otherwise (mirrors the Angular type/resolution filters). */
    private static String bucketedValue(String value, Set<String> knownValues, String otherBucket) {
        String v = value != null ? value : "";
        return knownValues.contains(v) ? v : otherBucket;
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

    /** Whether the numeric suffix of the key (eg "123" in "PROJ-123") falls within [fromNumber, toNumber] (inclusive, either bound optional). */
    private static boolean matchesNumberRange(String key, Integer fromNumber, Integer toNumber) {
        if (fromNumber == null && toNumber == null) {
            return true;
        }
        Integer number = issueNumberOf(key);
        if (number == null) {
            return false;
        }
        if (fromNumber != null && number < fromNumber) {
            return false;
        }
        if (toNumber != null && number > toNumber) {
            return false;
        }
        return true;
    }

    private static Integer issueNumberOf(String key) {
        if (key == null) {
            return null;
        }
        int dashIdx = key.lastIndexOf('-');
        if (dashIdx < 0 || dashIdx == key.length() - 1) {
            return null;
        }
        try {
            return Integer.parseInt(key.substring(dashIdx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The issue creator's username, falling back to the reporter, when missing. */
    private static String creatorOf(JiraIssueDTO issue) {
        String name = issue.fields != null ? issue.fields.creator : null;
        if (name == null || name.isBlank()) {
            name = issue.fields != null ? issue.fields.reporter : null;
        }
        return name != null && !name.isBlank() ? name : UNKNOWN_USER;
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
