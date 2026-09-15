package fr.an.projectanalysis.mailinglist.rest;

import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageAnnotationDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.PersonalInterrestMailCommentDTO;
import fr.an.projectanalysis.mailinglist.service.MailMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/mailing-list-message-annotations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "MailMessages")
@Slf4j
public class MailMessageAnnotationRestController {

    private static final String BASE_URL = "/api/v1/mailing-list-message-annotations";

    private final MailMessageService delegate;

    public MailMessageAnnotationRestController(MailMessageService delegate) {
        this.delegate = delegate;
    }


    @Operation(summary = "Query annotations for mailing-list messages, by archived month range")
    @GetMapping("/")
    public List<MailMessageAnnotationDTO> queryMessagesWithAnnotations(
            @RequestParam(name = "fromMonth", required = false) String fromMonth,
            @RequestParam(name = "toMonth", required = false) String toMonth
    ) {
        log.info("http GET {}?fromMonth={}&toMonth={}", BASE_URL, fromMonth, toMonth);
        return delegate.listMessageAnnotations(fromMonth, toMonth);
    }

    @Operation(summary = "Put annotation for mailing-list message")
    @PutMapping()
    public void putAnnotation(@RequestBody MailMessageAnnotationDTO req) {
        log.info("http PUT {} (messageId={})", BASE_URL, req.messageId);
        delegate.putAnnotation(req.messageId, req.annotated);
    }

    @Operation(summary = "Put personal interest comment for mailing-list message")
    @PutMapping("/personnal-interrest")
    public void putPersonalInterrestComment(@RequestBody PersonalInterrestMailCommentDTO req) {
        log.info("http PUT {}/personnal-interrest (messageId={})", BASE_URL, req.messageId);
        delegate.putPersonalInterrestComment(req.messageId, req.personalInterrestComment, req.personalInterrestPriority10);
    }

    @Operation(summary = "Delete annotation for mailing-list message")
    @DeleteMapping("/{messageId}")
    public void removeAnnotation(@PathVariable(name = "messageId") String messageId) {
        log.info("http DELETE {} (messageId={})", BASE_URL, messageId);
        delegate.removeAnnotation(messageId);
    }


}
