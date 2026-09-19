package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.rest.dtos.IssueExtraFieldsDTO;
import fr.an.projectanalysis.jira.rest.dtos.IssuesCriteriaDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import fr.an.projectanalysis.util.CritUtils;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Whether a {@link JiraIssueDTO} matches the Main/Analysis/Development Work/Personal Interest
 * filter criteria of the issues-list page (a null criteria matches everything).
 */
public class JiraIssueCriteria implements Predicate<JiraIssueDTO> {

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

    private final IssuesCriteriaDTO c;

    public JiraIssueCriteria(IssuesCriteriaDTO c) {
        this.c = c;
    }

    @Override
    public boolean test(JiraIssueDTO issue) {
        if (c == null) {
            return true;
        }
        JiraIssueDTO.IssueFieldsDTO fields = issue.fields;
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

        IssueExtraFieldsDTO annotated = issue.annotated;
        boolean hasAnalysis = annotated != null && annotated.analysisSummary != null && !annotated.analysisSummary.isBlank();
        if (!CritUtils.matchesAvailability(c.analysisAvailability, hasAnalysis)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.analysisSummaryContains, annotated != null ? annotated.analysisSummary : null)) {
            return false;
        }
        if (!CritUtils.matchesDateRange(c.analysisSummaryUpdatedFrom, c.analysisSummaryUpdatedTo,
                annotated != null ? annotated.analysisSummaryLastUpdateTime : null)) {
            return false;
        }
        if (!CritUtils.matchesTokensRangeK(c.analysisSummaryMinTokensK, c.analysisSummaryMaxTokensK,
                annotated != null ? annotated.analysisSummaryTokensConsumed : 0)) {
            return false;
        }
        List<String> analysisExtraPrompts = annotated != null && annotated.analysisUserExtraPrompts != null
                ? annotated.analysisUserExtraPrompts : List.of();
        if (!CritUtils.matchesAny(c.analysisUserExtraPromptsContains, analysisExtraPrompts.toArray(String[]::new))) {
            return false;
        }

        boolean hasDevWork = annotated != null && annotated.developmentWorkDescribed != null && !annotated.developmentWorkDescribed.isBlank();
        if (!CritUtils.matchesAvailability(c.developmentWorkAvailability, hasDevWork)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.developmentWorkDescribedContains, annotated != null ? annotated.developmentWorkDescribed : null)) {
            return false;
        }
        if (!CritUtils.matchesDateRange(c.developmentWorkUpdatedFrom, c.developmentWorkUpdatedTo,
                annotated != null ? annotated.developmentWorkLastUpdateTime : null)) {
            return false;
        }
        if (!CritUtils.matchesTokensRangeK(c.developmentWorkMinTokensK, c.developmentWorkMaxTokensK,
                annotated != null ? annotated.developmentWorkTokensConsumed : 0)) {
            return false;
        }
        List<String> devWorkExtraPrompts = annotated != null && annotated.developmentWorkUserExtraPrompts != null
                ? annotated.developmentWorkUserExtraPrompts : List.of();
        if (!CritUtils.matchesAny(c.developmentWorkUserExtraPromptsContains, devWorkExtraPrompts.toArray(String[]::new))) {
            return false;
        }

        boolean hasPersonalInterrest = annotated != null && annotated.personalInterrestComment != null && !annotated.personalInterrestComment.isBlank();
        if (!CritUtils.matchesAvailability(c.personalInterrestAvailability, hasPersonalInterrest)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.personalInterrestCommentContains, annotated != null ? annotated.personalInterrestComment : null)) {
            return false;
        }
        if (!CritUtils.matchesNumberRange(c.personalInterrestMinPriority, c.personalInterrestMaxPriority,
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

}
