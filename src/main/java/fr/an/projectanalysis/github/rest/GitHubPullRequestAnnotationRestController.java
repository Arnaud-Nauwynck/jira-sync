package fr.an.projectanalysis.github.rest;

import fr.an.projectanalysis.github.rest.dtos.GitHubPullRequestAnnotationDTO;
import fr.an.projectanalysis.github.rest.dtos.PersonalInterrestPrCommentDTO;
import fr.an.projectanalysis.github.service.GitHubPullRequestService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/github-pull-request-annotations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "GitHubPullRequests")
@Slf4j
public class GitHubPullRequestAnnotationRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/github-pull-request-annotations";

    private final GitHubPullRequestService delegate;

    public GitHubPullRequestAnnotationRestController(GitHubPullRequestService delegate) {
        super(BASE_URL);
        this.delegate = delegate;
    }


    @Operation(summary = "Query annotations for pull requests, by created year range")
    @GetMapping("/")
    public List<GitHubPullRequestAnnotationDTO> queryPullRequestsWithAnnotations(
            @RequestParam(name = "fromYear", defaultValue = "2020") int fromYear,
            @RequestParam(name = "toYear", defaultValue = "2050") int toYear
    ) {
        return withLog(log, "GET", "/", "fromYear=" + fromYear + "&toYear=" + toYear,
                () -> delegate.listPullRequestAnnotations(fromYear, toYear));
    }

    @Operation(summary = "Put annotation for pull request")
    @PutMapping()
    public void putAnnotation(@RequestBody GitHubPullRequestAnnotationDTO req) {
        withLog(log, "PUT", "", "number=" + req.number, () -> delegate.putAnnotation(req.number, req.annotated));
    }

    @Operation(summary = "Put personal interest comment for pull request")
    @PutMapping("/personnal-interrest")
    public void putPersonalInterrestComment(@RequestBody PersonalInterrestPrCommentDTO req) {
        withLog(log, "PUT", "/personnal-interrest", "number=" + req.number,
                () -> delegate.putPersonalInterrestComment(req.number, req.personalInterrestComment, req.personalInterrestPriority10));
    }

    @Operation(summary = "Delete annotation for pull request")
    @DeleteMapping("/{number}")
    public void removeAnnotation(@PathVariable(name = "number") int number) {
        withLog(log, "DELETE", "/" + number, "", () -> delegate.removeAnnotation(number));
    }

}
