package fr.an.projectanalysis.claude.service;

import fr.an.projectanalysis.claude.rest.dto.ClaudeCodeBatchCriteriaDTO;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptBatchDTO;
import fr.an.projectanalysis.util.CritUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Predicate;

/**
 * Whether a {@link ClaudeCodePromptBatchDTO} matches the fromDate/toDate, prompt/outputResult
 * "contains" and output-tokens range filter criteria of the prompt-batches page (a null criteria
 * matches everything).
 */
public class ClaudeCodeBatchCriteria implements Predicate<ClaudeCodePromptBatchDTO> {

    private final ClaudeCodeBatchCriteriaDTO c;

    public ClaudeCodeBatchCriteria(ClaudeCodeBatchCriteriaDTO c) {
        this.c = c;
    }

    @Override
    public boolean test(ClaudeCodePromptBatchDTO batch) {
        if (c == null) {
            return true;
        }
        if (!CritUtils.matchesDateRange(c.fromDate, c.toDate, startDateTimeOf(batch))) {
            return false;
        }
        if (!CritUtils.matchesAny(c.promptContains, batch.prompt)) {
            return false;
        }
        if (!CritUtils.matchesAny(c.outputResultContains, batch.outputResult)) {
            return false;
        }
        if (!matchesLongRange(c.minOutputTokens, c.maxOutputTokens, batch.outputTokens)) {
            return false;
        }
        return true;
    }

    private static LocalDateTime startDateTimeOf(ClaudeCodePromptBatchDTO batch) {
        return batch.startTime > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(batch.startTime), ZoneId.systemDefault())
                : null;
    }

    private static boolean matchesLongRange(Long min, Long max, Long value) {
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (min != null && value < min) {
            return false;
        }
        if (max != null && value > max) {
            return false;
        }
        return true;
    }

}
