package fr.an.projectanalysis.mailinglist.rest;

import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessagePartitionStatsDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageQueryDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.NearbyMailMessagesDTO;
import fr.an.projectanalysis.mailinglist.service.MailMessageService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping(path = "/api/v1/mailing-list-messages", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "MailMessages")
@Slf4j
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
        return withLog(log, "GET", "/message", "messageId=" + messageId, () -> {
            MailMessageDTO found = delegate.findByMessageId(messageId);
            return found != null ? ResponseEntity.ok(found) : ResponseEntity.notFound().build();
        });
    }

    @Operation(summary = "For the message with the given Message-ID, finds the Message-IDs of the nearest earlier "
            + "(\"prev\") and later (\"next\") message overall, and the nearest one from the same sender")
    @GetMapping("/message/nearby")
    public NearbyMailMessagesDTO findNearbyMessages(@RequestParam("messageId") String messageId) {
        return withLog(log, "GET", "/message/nearby", "messageId=" + messageId, () -> delegate.findNearbyMessages(messageId));
    }

    @Operation(summary = "List messages matching the given criteria (Data Fetching + Main/Analysis/Development Work/Personal Interest "
            + "filter criteria of the mailing-list page), capped at the given limit (default 1000)")
    @PostMapping("/query")
    public Collection<MailMessageDTO> queryMessages(@RequestBody MailMessageQueryDTO query) {
        return withLog(log, "POST", "/query", "limit=" + (query != null ? query.limit : null),
                () -> delegate.queryMessages(query));
    }

    @Operation(summary = "Same as /query, but returns only the matching message ids, without fetching the full message objects")
    @PostMapping("/query-ids")
    public Collection<String> queryMessageIds(@RequestBody MailMessageQueryDTO query) {
        return withLog(log, "POST", "/query-ids", "limit=" + (query != null ? query.limit : null),
                () -> delegate.queryMessageIds(query));
    }

    @Operation(summary = "Count of locally-synced messages per \"archived\" (month) partition, and per sender")
    @GetMapping("/partition-stats")
    public MailMessagePartitionStatsDTO queryPartitionStats() {
        return withLog(log, "GET", "/partition-stats", "", delegate::queryPartitionStats);
    }

}
