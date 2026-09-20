package fr.an.projectanalysis.claude.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Element of the response body for {@code GET .../claude-code/calls}, describing one currently
 * running {@code claude} CLI prompt invocation. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor @AllArgsConstructor
public class ClaudeCodePromptRunningBatchDTO {

    public long id;
    public String prompt;
    public List<String> allowedTools;
    public long startTime;
    public long pid;

}
