package fr.an.projectanalysis.claude.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response body for {@code POST .../claude-code/prompt}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor @AllArgsConstructor
public class ClaudeCodePromptResponseDTO {

    public long runningBatchId;

}
