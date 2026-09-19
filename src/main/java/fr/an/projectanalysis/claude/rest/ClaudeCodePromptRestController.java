package fr.an.projectanalysis.claude.rest;

import fr.an.projectanalysis.rest.dtos.ClaudeCodePromptRequestDTO;
import fr.an.projectanalysis.rest.dtos.ClaudeCodePromptResponseDTO;
import fr.an.projectanalysis.claude.service.ClaudeCodePromptInvokerService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes {@link ClaudeCodePromptInvokerService} for one-shot prompt invocation of the local claude CLI. */
@RestController
@RequestMapping(path = "/api/v1/claude-code", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "ClaudeCode")
@Slf4j
public class ClaudeCodePromptRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/claude-code";

    private final ClaudeCodePromptInvokerService delegate;

    public ClaudeCodePromptRestController(ClaudeCodePromptInvokerService delegate) {
        super(BASE_URL);
        this.delegate = delegate;
    }

    @Operation(summary = "Invoke the local claude CLI with a one-shot prompt, and return its JSON output")
    @PostMapping("/prompt")
    public ClaudeCodePromptResponseDTO invokePrompt(@RequestBody ClaudeCodePromptRequestDTO req) {
        return withLog(log, "POST", "/prompt", "", () -> {
            String output = delegate.invokePrompt(req.prompt);
            return new ClaudeCodePromptResponseDTO(output);
        });
    }

}
