package fr.an.jira.rest;

import fr.an.jira.rest.dtos.JiraIssueAnnotationDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO;
import fr.an.jira.rest.dtos.UserIssueCreateStatsDTO;
import fr.an.jira.service.JiraIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping(path="/api/v1/jira-issue-annotations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "JiraIssues")
@Slf4j
public class JiraIssueAnnotationRestController {

    private static final String BASE_URL = "/api/v1/jira-issue-annotation";

    private final JiraIssueService delegate;

    public JiraIssueAnnotationRestController(JiraIssueService delegate) {
        this.delegate = delegate;
    }


    @Operation(summary = "Query annotations for issues, by years")
    @GetMapping("/")
    public List<JiraIssueAnnotationDTO> queryIssueWithAnnotations(
            @RequestParam(name="fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name="toYear", defaultValue = "2050") int toYear
            ) {
        log.info("http GET {}?fromYear={}&toYear={}", BASE_URL, fromYear, toYear);
        return delegate.listIssueAnnotations(fromYear, toYear);
    }

    @Operation(summary = "Put annotation for issue")
    @PutMapping()
    public void putAnnotation(@RequestBody JiraIssueAnnotationDTO req) {
        log.info("http POST {} (key={})", BASE_URL, req.key);
        delegate.putAnnotation(req.key, req.annotated);
    }

    @Operation(summary = "Delete annotation for issue")
    @DeleteMapping("/{key}")
    public void putAnnotation(@PathVariable(name="key") String key) {
        log.info("http DELETE {} (key={})", BASE_URL, key);
        delegate.removeAnnotation(key);
    }


}
