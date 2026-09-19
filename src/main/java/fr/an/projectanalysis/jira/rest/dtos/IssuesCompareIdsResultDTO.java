package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Result of {@code POST .../jira-issues/compare-query-ids}: the ids matched by only one side, or by both. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IssuesCompareIdsResultDTO {

    /** Ids matched by the left criteria only, in left query order. */
    public List<String> leftOnlyIds;

    /** Ids matched by both criteria, in left query order; filled only when {@code fillCommonIds} was requested. */
    public List<String> commonIds;

    /** Count of ids matched by both criteria, always filled (even when {@code commonIds} is not). */
    public int commonCount;

    /** Ids matched by the right criteria only, in right query order. */
    public List<String> rightOnlyIds;

}
