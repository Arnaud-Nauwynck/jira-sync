package fr.an.projectanalysis.claude.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Filter criteria for {@code ClaudeCodePromptInvokerService.queryPromptBatches}, mirroring the
 * Search Criteria panel of the claude-code-prompt-batches Angular page. Every field is optional;
 * an unset field does not filter on that criterion.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeCodeBatchCriteriaDTO {

    /** Inclusive lower bound on {@code startTime}, as a 'yyyy-MM-dd' date. */
    public String fromDate;

    /** Inclusive upper bound on {@code startTime}, as a 'yyyy-MM-dd' date. */
    public String toDate;

    /** Case-insensitive substring searched in {@code prompt}. */
    public String promptContains;

    /** Case-insensitive substring searched in {@code outputResult}. */
    public String outputResultContains;

    public Long minOutputTokens;

    public Long maxOutputTokens;

}
