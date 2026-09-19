package fr.an.projectanalysis.jira.rest;

import fr.an.projectanalysis.jira.rest.dtos.JiraIssueAnnotationDTO;
import fr.an.projectanalysis.jira.rest.dtos.PersonalInterrestCommentDTO;
import fr.an.projectanalysis.jira.service.JiraIssueService;
import fr.an.projectanalysis.rest.dtos.ClaudeCodePromptResponseDTO;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path="/api/v1/jira-issue-annotations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "JiraIssues")
@Slf4j
public class JiraIssueAnnotationRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/jira-issue-annotation";

    private final JiraIssueService delegate;

    public JiraIssueAnnotationRestController(JiraIssueService delegate) {
        super(BASE_URL);
        this.delegate = delegate;
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
                () -> delegate.launchClaudeJiraAnalysis(jiraKey));
    }

}
