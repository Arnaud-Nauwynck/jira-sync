package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** Request body for {@code POST .../jira-issues/query} and {@code .../query-ids}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IssuesQueryDTO {

    public IssuesCriteriaDTO criteria;

    /** Caps the number of results; defaults to 1000 when unset. */
    public Integer limit;

}
