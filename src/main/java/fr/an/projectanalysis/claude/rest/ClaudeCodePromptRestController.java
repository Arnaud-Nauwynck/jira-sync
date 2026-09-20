package fr.an.projectanalysis.claude.rest;

import fr.an.projectanalysis.claude.rest.dto.ClaudeCodeBatchCriteriaDTO;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptBatchDTO;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptRunningBatchDTO;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptRequestDTO;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptResponseDTO;
import fr.an.projectanalysis.claude.service.ClaudeCodePromptInvokerService;
import fr.an.projectanalysis.claude.service.ClaudeCodePromptRunningBatch;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
            ClaudeCodePromptRunningBatch runningBatch = delegate.invokePrompt(req.prompt);
            return new ClaudeCodePromptResponseDTO(runningBatch.getRunningBatchId());
        });
    }

    @Operation(summary = "List the Claude CLI prompt invocations currently running")
    @GetMapping("/current-running-batches")
    public List<ClaudeCodePromptRunningBatchDTO> getCurrentRunningBatches() {
        return withLogDebug(log, "GET", "/calls", "", () -> delegate.getCurrentRunningBatchDTOs());
    }

    @Operation(summary = "List all finished Claude CLI prompt invocations persisted so far")
    @GetMapping("/prompt-batches")
    public List<ClaudeCodePromptBatchDTO> getPromptBatches() {
        return withLogDebug(log, "GET", "/prompt-batches", "", () -> delegate.getPromptBatches());
    }

    @Operation(summary = "List the finished Claude CLI prompt invocations persisted for a single partition year")
    @GetMapping("/prompt-batches/{year}")
    public List<ClaudeCodePromptBatchDTO> getPromptBatchesByYear(@PathVariable("year") int year) {
        return withLogDebug(log, "GET", "/prompt-batches/" + year, "", () -> delegate.getPromptBatches(year));
    }

    @Operation(summary = "List the partition years having at least one persisted finished prompt invocation")
    @GetMapping("/prompt-batch-years")
    public List<Integer> getPromptBatchYears() {
        return withLogDebug(log, "GET", "/prompt-batch-years", "", () -> delegate.getPromptBatchYears());
    }

    @Operation(summary = "List finished Claude CLI prompt invocations matching the given search criteria "
            + "(fromDate/toDate, prompt/outputResult contains, output tokens range)")
    @PostMapping("/query")
    public List<ClaudeCodePromptBatchDTO> queryPromptBatches(@RequestBody ClaudeCodeBatchCriteriaDTO criteria) {
        return withLog(log, "POST", "/query", "", () -> delegate.queryPromptBatches(criteria));
    }

}
