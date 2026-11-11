package fr.an.projectanalysis.jira.rest;

import fr.an.projectanalysis.jira.rest.dtos.JiraProjectComponentDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraProjectVersionDTO;
import fr.an.projectanalysis.jira.service.JiraReferentialService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Rest controller for Jira Referential.
 * delegate all endpoint calls to JiraReferentialService
 */
@RestController
@RequestMapping(path="/api/v1/jira-referential", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "JiraReferential")
@Slf4j
public class JiraReferentialRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/jira-referential";

    private final JiraReferentialService delegate;

    public JiraReferentialRestController(JiraReferentialService delegate) {
        super(BASE_URL);
        this.delegate = delegate;
    }

    @Operation(summary = "Get project versions")
    @GetMapping("/project/versions")
    public List<JiraProjectVersionDTO> getProjectVersions() {
        return withLogDebug(log, "GET", "/project-versions", "",
                delegate::getProjectVersions);
    }

    @Operation(summary = "Get project not archived versions")
    @GetMapping("/project/not-archived-versions")
    public List<JiraProjectVersionDTO> getProjectNotArchivedVersions() {
        return withLogDebug(log, "GET", "/project/notarchived-versions", "",
                delegate::getProjectNotArchivedVersions);
    }

    @Operation(summary = "Get project components")
    @GetMapping("/project/components")
    public List<JiraProjectComponentDTO> getProjectComponents() {
        return withLogDebug(log, "GET", "/project/components", "",
                delegate::getProjectComponents);
    }

}