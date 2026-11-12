package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Request body for {@code POST .../jira-issues/timeline}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IssuesTimelineQueryDTO {

    /** First day (ISO "yyyy-MM-dd", inclusive) of the timeline. */
    public String startDate;

    /** Last day (ISO "yyyy-MM-dd", inclusive) of the timeline. */
    public String endDate;

    /** Size of the time buckets; defaults to ONE_DAY when unset. */
    public IssuesTimelineBucketBy bucketBy;

    /** When true, the counts are running totals, starting from the issues created before startDate. */
    public boolean countOpenedBefore;

    /** When true or unset, counts the issues created per bucket. When false, counts instead the issues still open
     * (created and not yet resolved) at the end of each bucket, among the issues created from startDate (or before,
     * when countOpenedBefore is set). */
    public Boolean creationOnly;

    /** Project component names not to display; the "(no component)" pseudo-component can be excluded too. */
    public List<String> excludedComponents;

    /** Issue types not to count. */
    public List<String> excludedIssueTypes;

}
