package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** Request body for {@code POST .../jira-issues/compare-query-ids}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IssuesCompareQueryDTO {

    /** Criteria of the "left" side of the comparison; a null criteria matches every issue. */
    public IssuesCriteriaDTO leftCriteria;

    /** Caps the number of results of the left side; defaults to 1000 when unset. */
    public Integer leftLimit;

    /** Criteria of the "right" side of the comparison; a null criteria matches every issue. */
    public IssuesCriteriaDTO rightCriteria;

    /** Caps the number of results of the right side; defaults to 1000 when unset. */
    public Integer rightLimit;

    /** When true, the result also lists the ids matched by both sides, and not only their count. */
    public boolean fillCommonIds;


}
