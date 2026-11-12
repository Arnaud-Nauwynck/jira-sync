package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Response of {@code POST .../jira-issues/timeline}: issues creation counts per time bucket, issue type and component. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IssuesTimelineDTO {

    /** Start of each time bucket, in epoch milliseconds (UTC); shared by all the series. */
    public List<Long> bucketStarts;

    public List<ComponentTimelineDTO> components;

    /** Timeline of the issues of 1 project component. */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComponentTimelineDTO {
        /** Component name, or "(no component)". */
        public String component;
        /** Count of distinct issues of this component, created up to the end date (and from the start date, unless countOpenedBefore). */
        public int issueCount;
        public List<IssueTypeSeriesDTO> series;
    }

    /** 1 count per time bucket (aligned with {@link IssuesTimelineDTO#bucketStarts}), for 1 issue type. */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IssueTypeSeriesDTO {
        public String issueType;
        public List<Integer> counts;
    }

}
