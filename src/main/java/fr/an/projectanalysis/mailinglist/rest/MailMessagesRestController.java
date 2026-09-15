package fr.an.projectanalysis.mailinglist.rest;

import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.projectanalysis.mailinglist.service.MailMessageService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping(path = "/api/v1/mailing-list-messages", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "MailMessages")
public class MailMessagesRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/mailing-list-messages";

    private final MailMessageService delegate;

    public MailMessagesRestController(MailMessageService delegate) {
        super(BASE_URL);
        this.delegate = delegate;
    }

    @Operation(summary = "Find a single locally-synced mailing-list message by its Message-ID")
    @GetMapping("/message")
    public ResponseEntity<MailMessageDTO> findMessageByMessageId(@RequestParam("messageId") String messageId) {
        return withLog("GET", "/message", "messageId=" + messageId, () -> {
            MailMessageDTO found = delegate.findByMessageId(messageId);
            return found != null ? ResponseEntity.ok(found) : ResponseEntity.notFound().build();
        });
    }

    @Operation(summary = "List mailing-list messages archived between fromMonth and toMonth (both 'yyyy-MM', inclusive), optionally filtered by regexes matched against the From header, the Subject, and/or the body text")
    @GetMapping("/messages")
    public Collection<MailMessageDTO> queryMessages(
            @RequestParam(name = "fromMonth", required = false) String fromMonth,
            @RequestParam(name = "toMonth", required = false) String toMonth,
            @RequestParam(name = "fromPattern", required = false) String fromPattern,
            @RequestParam(name = "subjectPattern", required = false) String subjectPattern,
            @RequestParam(name = "bodyPattern", required = false) String bodyPattern
    ) {
        String paramsText = "fromMonth=" + fromMonth + "&toMonth=" + toMonth + "&fromPattern=" + fromPattern
                + "&subjectPattern=" + subjectPattern + "&bodyPattern=" + bodyPattern;
        return withLog("GET", "/messages", paramsText,
                () -> delegate.queryMessages(fromMonth, toMonth, fromPattern, subjectPattern, bodyPattern));
    }

}
