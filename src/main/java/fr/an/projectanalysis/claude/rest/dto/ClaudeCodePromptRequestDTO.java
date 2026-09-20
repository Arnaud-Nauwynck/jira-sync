package fr.an.projectanalysis.claude.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** Request body for {@code POST .../claude-code/prompt}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeCodePromptRequestDTO {

    public String prompt;

}
