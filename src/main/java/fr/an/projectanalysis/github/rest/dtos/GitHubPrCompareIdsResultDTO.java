package fr.an.projectanalysis.github.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Result of {@code POST .../github-pull-requests/compare-query-ids}: the PR numbers matched by only one side, or by both. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitHubPrCompareIdsResultDTO {

    /** PR numbers matched by the left criteria only, in left query order. */
    public List<Integer> leftOnlyIds;

    /** PR numbers matched by both criteria, in left query order; filled only when {@code fillCommonIds} was requested. */
    public List<Integer> commonIds;

    /** Count of PR numbers matched by both criteria, always filled (even when {@code commonIds} is not). */
    public int commonCount;

    /** PR numbers matched by the right criteria only, in right query order. */
    public List<Integer> rightOnlyIds;

}
