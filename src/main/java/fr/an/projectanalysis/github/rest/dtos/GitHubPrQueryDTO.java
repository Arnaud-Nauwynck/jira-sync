package fr.an.projectanalysis.github.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** Request body for {@code POST .../github-pull-requests/query} and {@code .../query-ids}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitHubPrQueryDTO {

    public GitHubPrCriteriaDTO criteria;

    /** Caps the number of results; defaults to 1000 when unset. */
    public Integer limit;

}
