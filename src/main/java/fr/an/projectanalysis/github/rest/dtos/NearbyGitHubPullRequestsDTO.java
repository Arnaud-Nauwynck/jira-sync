package fr.an.projectanalysis.github.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Numbers of the pull requests nearest to a given PR, on the "prev" (earlier) and "next" (later)
 * sides, matching each of 3 independent criteria: still open, still open and created by the same
 * author, and created by the same author (regardless of state). Any field is null when no such
 * PR exists.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NearbyGitHubPullRequestsDTO {

    public Integer prevStillOpen;
    public Integer prevStillOpenCreatedBySameAuthor;
    public Integer prevCreatedBySameAuthor;

    public Integer nextStillOpen;
    public Integer nextStillOpenCreatedBySameAuthor;
    public Integer nextCreatedBySameAuthor;

}
