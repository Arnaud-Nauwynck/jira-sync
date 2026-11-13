package fr.an.projectanalysis.jira.rest;

import fr.an.projectanalysis.jira.rest.dtos.JiraIssueAnnotationDTO;
import fr.an.projectanalysis.jira.rest.dtos.PersonalInterrestCommentDTO;
import fr.an.projectanalysis.jira.service.JiraAnalysisBatchService;
import fr.an.projectanalysis.jira.service.JiraAnalysisLauncher;
import fr.an.projectanalysis.jira.service.JiraAnalysesTextExportService;
import fr.an.projectanalysis.jira.service.JiraIssueService;
import fr.an.projectanalysis.claude.rest.dto.ClaudeCodePromptResponseDTO;
import fr.an.projectanalysis.util.AbstractRestController;
import fr.an.projectanalysis.util.LsUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path="/api/v1/jira-issue-annotations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "JiraIssues")
@Slf4j
public class JiraIssueAnnotationRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/jira-issue-annotation";

    private final JiraIssueService delegate;

    private final JiraAnalysisLauncher jiraAnalysisLauncher;

    private final JiraAnalysisBatchService jiraAnalysisBatchService;

    private final JiraAnalysesTextExportService analysesTextExportService;

    public JiraIssueAnnotationRestController(JiraIssueService delegate, JiraAnalysisLauncher jiraAnalysisLauncher,
            JiraAnalysisBatchService jiraAnalysisBatchService, JiraAnalysesTextExportService analysesTextExportService) {
        super(BASE_URL);
        this.delegate = delegate;
        this.jiraAnalysisLauncher = jiraAnalysisLauncher;
        this.jiraAnalysisBatchService = jiraAnalysisBatchService;
        this.analysesTextExportService = analysesTextExportService;
    }


    @Operation(summary = "Query annotations for issues, by years")
    @GetMapping("/")
    public List<JiraIssueAnnotationDTO> queryIssueWithAnnotations(
            @RequestParam(name="fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name="toYear", defaultValue = "2050") int toYear
            ) {
        return withLog(log, "GET", "/", "fromYear=" + fromYear + "&toYear=" + toYear,
                () -> delegate.listIssueAnnotations(fromYear, toYear));
    }

    @Operation(summary = "Put annotation for issue")
    @PutMapping()
    public void putAnnotation(@RequestBody JiraIssueAnnotationDTO req) {
        withLog(log, "PUT", "", "key=" + req.key, () -> delegate.putAnnotation(req.key, req.annotated));
    }

    @Operation(summary = "Put personal interest comment for issue")
    @PutMapping("/personnal-interrest")
    public void putPersonalInterrestComment(@RequestBody PersonalInterrestCommentDTO req) {
        withLog(log, "PUT", "/personnal-interrest", "key=" + req.key,
                () -> delegate.putPersonalInterrestComment(req.key, req.personalInterrestComment, req.personalInterrestPriority10));
    }

    @Operation(summary = "Delete annotation for issue")
    @DeleteMapping("/{key}")
    public void putAnnotation(@PathVariable(name="key") String key) {
        withLog(log, "DELETE", "/" + key, "", () -> delegate.removeAnnotation(key));
    }

    @Operation(summary = "Launch a claude-code '/jira-analysis' prompt for the given issue")
    @PostMapping("/launch-claude-jira-analysis")
    public ClaudeCodePromptResponseDTO launchClaudeJiraAnalysis(@RequestParam(name = "jiraKey") String jiraKey) {
        return withLog(log, "POST", "/launch-claude-jira-analysis", "jiraKey=" + jiraKey,
                () -> {
                    val runningBatch = jiraAnalysisLauncher.launchClaudeJiraAnalysisOrSkip(jiraKey);
                    return new ClaudeCodePromptResponseDTO(runningBatch.getRunningBatchId());
                });
    }

    @Operation(summary = "Launch a claude-code '/jira-analysis' prompt for each of the given issues")
    @PostMapping("/analyze-all")
    public List<ClaudeCodePromptResponseDTO> analyzeAll(@RequestBody List<String> jiraKeys) {
        return withLog(log, "POST", "/analyze-all", "count=" + jiraKeys.size(),
                () -> LsUtil.map(jiraAnalysisBatchService.analyzeAll(jiraKeys),
                        x -> new ClaudeCodePromptResponseDTO(x.getRunningBatchId())));
    }

    @Operation(summary = "Launch in parallel a claude-code '/jira-analysis' prompt for each of the given issues")
    @PostMapping("/analyze-all-in-parallel")
    public List<ClaudeCodePromptResponseDTO> analyzeAllInParallel(@RequestBody List<String> jiraKeys) {
        return withLog(log, "POST", "/analyze-all-in-parallel", "count=" + jiraKeys.size(),
                () -> LsUtil.map(jiraAnalysisBatchService.analyzeAllInParallel(jiraKeys),
                        x -> new ClaudeCodePromptResponseDTO(x.getRunningBatchId())));
    }

    @Operation(summary = "Download a text document with all Jira summaries, each followed by its analysis text")
    @GetMapping(path = "/download-all-analyses", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> downloadAllAnalyses(
            @RequestParam(name="fromYear", defaultValue = "2000") int fromYear,
            @RequestParam(name="toYear", defaultValue = "2050") int toYear) {
        String text = withLog(log, "GET", "/download-all-analyses", "fromYear=" + fromYear + "&toYear=" + toYear,
                () -> analysesTextExportService.exportAllAnalyses(fromYear, toYear));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jira-analyses.txt\"")
                .body(text);
    }

}
