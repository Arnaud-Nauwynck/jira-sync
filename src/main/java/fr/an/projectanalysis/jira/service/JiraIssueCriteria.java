package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.rest.dtos.IssuesCriteriaDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.util.AnnotatedCritUtils;
import fr.an.projectanalysis.util.CritUtils;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Whether a {@link JiraIssueDTO} matches the Data Fetching + Main/Analysis/Development Work/
 * Personal Interest filter criteria of the issues-list page (a null criteria matches everything).
 */
public class JiraIssueCriteria implements Predicate<JiraIssueDTO> {

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

    /** Default "created_year" partition range, when the criteria does not restrict it. */
    private static final int DEFAULT_FROM_YEAR = 2020;
    private static final int DEFAULT_TO_YEAR = 2050;

    private final IssuesCriteriaDTO c;

    private final Pattern usernamePattern;

    private final Pattern keyPattern;

    private final Pattern summaryPattern;

    private final Pattern descriptionPattern;

    private final Pattern commentPattern;

    private final Pattern commentAuthorPattern;

    public JiraIssueCriteria(IssuesCriteriaDTO c) {
        this.c = c;
        this.usernamePattern = c != null ? CritUtils.compilePattern(c.usernamePattern) : null;
        this.keyPattern = c != null ? CritUtils.compilePattern(c.keyPattern) : null;
        this.summaryPattern = c != null ? CritUtils.compilePattern(c.summaryPattern) : null;
        this.descriptionPattern = c != null ? CritUtils.compilePattern(c.descriptionPattern) : null;
        this.commentPattern = c != null ? CritUtils.compilePattern(c.commentPattern) : null;
        this.commentAuthorPattern = c != null ? CritUtils.compilePattern(c.commentAuthorPattern) : null;
    }

    /** Criteria filtering only on the creator/reporter username regex. */
    public static JiraIssueCriteria ofUsernamePattern(String usernamePatternText) {
        IssuesCriteriaDTO c = new IssuesCriteriaDTO();
        c.usernamePattern = usernamePatternText;
        return new JiraIssueCriteria(c);
    }

    /** Criteria of the per-user issue creation stats: a "created_year" range, plus the creator/summary/
     * description/comment regexes (all optional, combined with AND). */
    public static JiraIssueCriteria ofUserStatsPatterns(
            int fromYear, int toYear,
            String usernamePatternText,
            String summaryPatternText,
            String descriptionPatternText,
            String commentPatternText,
            String commentAuthorPatternText
    ) {
        IssuesCriteriaDTO c = new IssuesCriteriaDTO();
        c.fromYear = fromYear;
        c.toYear = toYear;
        c.usernamePattern = usernamePatternText;
        c.summaryPattern = summaryPatternText;
        c.descriptionPattern = descriptionPatternText;
        c.commentPattern = commentPatternText;
        c.commentAuthorPattern = commentAuthorPatternText;
        return new JiraIssueCriteria(c);
    }

    /** Earliest "created_year" partition to scan (inclusive), defaulting to {@value #DEFAULT_FROM_YEAR}. */
    public int getFromYear() {
        return (c != null && c.fromYear != null) ? c.fromYear : DEFAULT_FROM_YEAR;
    }

    /** Latest "created_year" partition to scan (inclusive), defaulting to {@value #DEFAULT_TO_YEAR}. */
    public int getToYear() {
        return (c != null && c.toYear != null) ? c.toYear : DEFAULT_TO_YEAR;
    }

    /** The issue creator's username, falling back to the reporter, then to {@code "unknown"}, when missing. */
    public static String creatorOf(JiraIssueDTO issue) {
        String name = issue.fields != null ? issue.fields.creator : null;
        if (name == null || name.isBlank()) {
            name = issue.fields != null ? issue.fields.reporter : null;
        }
        return name != null && !name.isBlank() ? name : UNKNOWN_USER;
    }

    /** The numeric suffix of an issue key (eg 123 in "PROJ-123"), or null when there is none. */
    public static Integer issueNumberOf(String key) {
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

    @Override
    public boolean test(JiraIssueDTO issue) {
        if (c == null) {
            return true;
        }
        if (!CritUtils.matchesRegex(usernamePattern, creatorOf(issue))) {
            return false;
        }
        if (!CritUtils.matchesRegex(keyPattern, issue.key)) {
            return false;
        }
        if (!CritUtils.matchesNumberRange(c.fromNumber, c.toNumber, issueNumberOf(issue.key))) {
            return false;
        }
        JiraIssueDTO.IssueFieldsDTO fields = issue.fields;
        if (!CritUtils.findsRegex(summaryPattern, fields != null ? fields.summary : null)) {
            return false;
        }
        if (!CritUtils.findsRegex(descriptionPattern, fields != null ? fields.description : null)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.summaryContains, fields != null ? fields.summary : null)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.descriptionContains, fields != null ? fields.description : null)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.authorContains, fields != null ? fields.creator : null, fields != null ? fields.reporter : null)) {
            return false;
        }
        List<JiraIssueDTO.IssueCommentDTO> comments = fields != null && fields.comments != null ? fields.comments : List.of();
        if (!matchesCommentPatterns(comments)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.commentsContains, comments.stream().map(cm -> cm.body).toArray(String[]::new))) {
            return false;
        }
        if (!CritUtils.matchesAny(c.commentAuthorContains, comments.stream().map(cm -> cm.author).toArray(String[]::new))) {
            return false;
        }
        if (CritUtils.isExcluded(c.excludedTypes, bucketedValue(fields != null ? fields.issuetype : null, KNOWN_TYPES, OTHER_TYPES))) {
            return false;
        }
        if (CritUtils.isExcluded(c.excludedResolutions, bucketedValue(fields != null ? fields.resolution : null, KNOWN_RESOLUTIONS, OTHER_RESOLUTIONS))) {
            return false;
        }
        if (CritUtils.isExcluded(c.excludedStatuses, fields != null ? fields.status : null)) {
            return false;
        }
        if (CritUtils.isExcluded(c.excludedPriorities, fields != null ? fields.priority : null)) {
            return false;
        }
        List<String> labels = fields != null && fields.labels != null ? fields.labels : List.of();
        if (!CritUtils.matchesAny(c.labelsContains, labels.toArray(String[]::new))) {
            return false;
        }
        if (!CritUtils.matchesAvailability(c.pullRequestAvailableLabel, labels.contains(PULL_REQUEST_AVAILABLE_LABEL))) {
            return false;
        }
        List<String> components = fields != null && fields.components != null ? fields.components : List.of();
        if (!CritUtils.matchesAny(c.componentsContains, components.toArray(String[]::new))) {
            return false;
        }

        return AnnotatedCritUtils.matchesAnnotations(c, issue.annotated);
    }

    /** True when neither comment regex is set, or at least one comment matches both of them: the
     * {@code commentPattern} searched in its body, and the {@code commentAuthorPattern} fully matching its author. */
    private boolean matchesCommentPatterns(List<JiraIssueDTO.IssueCommentDTO> comments) {
        if (commentPattern == null && commentAuthorPattern == null) {
            return true;
        }
        for (JiraIssueDTO.IssueCommentDTO comment : comments) {
            if (CritUtils.findsRegex(commentPattern, comment.body)
                    && CritUtils.matchesRegex(commentAuthorPattern, comment.author)) {
                return true;
            }
        }
        return false;
    }

    /** Maps a value to itself if it is a known enum option, or to the "others" bucket otherwise (mirrors the Angular type/resolution filters). */
    private static String bucketedValue(String value, Set<String> knownValues, String otherBucket) {
        String v = value != null ? value : "";
        return knownValues.contains(v) ? v : otherBucket;
    }

}
