package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Keys of the issues nearest to a given issue (by issue number, within the same Jira project),
 * on the "prev" (earlier) and "next" (later) sides, matching each of 3 independent criteria:
 * still open, still open and created by the same author, and created by the same author
 * (regardless of status). Any field is null when no such issue exists.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NearbyJiraIssuesDTO {

    public String prevStillOpen;
    public String prevStillOpenCreatedBySameAuthor;
    public String prevCreatedBySameAuthor;

    public String nextStillOpen;
    public String nextStillOpenCreatedBySameAuthor;
    public String nextCreatedBySameAuthor;

}
