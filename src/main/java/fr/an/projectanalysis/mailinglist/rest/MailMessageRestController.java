package fr.an.projectanalysis.mailinglist.rest;

import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageCompareIdsResultDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageCompareQueryDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageIdAndLastUpdateTimeDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessagePartitionStatsDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageQueryDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.NearbyMailMessagesDTO;
import fr.an.projectanalysis.mailinglist.service.MailMessageCriteria;
import fr.an.projectanalysis.mailinglist.service.MailMessageService;
import fr.an.projectanalysis.util.AbstractRestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
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
import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/mailing-list-messages", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "MailMessages")
@Slf4j
public class MailMessageRestController extends AbstractRestController {

    private static final String BASE_URL = "/api/v1/mailing-list-messages";

    /** Caps the number of results of /query and /query-ids, when the request does not set its own limit. */
    private static final int DEFAULT_LIMIT = 1000;

    private final MailMessageService delegate;

    public MailMessageRestController(MailMessageService delegate) {
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

    @Operation(summary = "Find the messages having the given Message-IDs (\"ids\"), returned in the request order; "
            + "ids not found locally are silently skipped")
    @PostMapping("/by-ids")
    public Collection<MailMessageDTO> findByIds(@RequestBody @Nonnull List<String> ids) {
        return withLog(log, "POST", "/by-ids", "ids.size=" + ids.size(),
                () -> delegate.findByIds(ids));
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
        MailMessageCriteria messageCriteria = criteriaOf(query);
        int limit = limitOf(query);
        return withLog(log, "POST", "/query", "limit=" + limit,
                () -> delegate.queryMessages(messageCriteria, limit));
    }

    @Operation(summary = "Same as /query, but returns only the matching message ids, without fetching the full message objects")
    @PostMapping("/query-ids")
    public Collection<String> queryMessageIds(@RequestBody MailMessageQueryDTO query) {
        MailMessageCriteria messageCriteria = criteriaOf(query);
        int limit = limitOf(query);
        return withLog(log, "POST", "/query-ids", "limit=" + limit,
                () -> delegate.queryMessageIds(messageCriteria, limit));
    }

    @Operation(summary = "Same as /query-ids, but returns for each matching message its Message-ID (\"id\") with "
            + "its last update time (\"t\", in epoch milliseconds)")
    @PostMapping("/query-id-and-last-update-times")
    public Collection<MailMessageIdAndLastUpdateTimeDTO> queryMessageIdAndLastUpdateTimes(@RequestBody MailMessageQueryDTO query) {
        MailMessageCriteria messageCriteria = criteriaOf(query);
        int limit = limitOf(query);
        return withLog(log, "POST", "/query-id-and-last-update-times", "limit=" + limit,
                () -> delegate.queryMessageIdAndLastUpdateTimes(messageCriteria, limit));
    }

    @Operation(summary = "Compares the message ids matched by 2 independent criteria: ids matched by the left "
            + "criteria only, by both (listed only when fillCommonIds is true, else only counted), and by the right "
            + "criteria only; each side capped at its own limit (default 1000)")
    @PostMapping("/compare-query-ids")
    public MailMessageCompareIdsResultDTO compareQueryIds(@RequestBody @Nonnull MailMessageCompareQueryDTO query) {
        MailMessageCriteria leftCriteria = new MailMessageCriteria(query.leftCriteria);
        MailMessageCriteria rightCriteria = new MailMessageCriteria(query.rightCriteria);
        boolean fillCommonIds = query.fillCommonIds;
        int leftLimit = (query.leftLimit != null) ? query.leftLimit : DEFAULT_LIMIT;
        int rightLimit = (query.rightLimit != null) ? query.rightLimit : DEFAULT_LIMIT;
        return withLog(log, "POST", "/compare-query-ids", "fillCommonIds=" + fillCommonIds
                        + "&leftLimit=" + leftLimit + "&rightLimit=" + rightLimit,
                () -> delegate.compareQueryIds(leftCriteria, leftLimit, rightCriteria, rightLimit, fillCommonIds),
                res -> res.leftOnlyIds.size() + " left-only, " + res.commonCount + " common, "
                        + res.rightOnlyIds.size() + " right-only");
    }

    @Operation(summary = "Count of locally-synced messages per \"archived\" (month) partition, and per sender")
    @GetMapping("/partition-stats")
    public MailMessagePartitionStatsDTO queryPartitionStats() {
        return withLog(log, "GET", "/partition-stats", "", delegate::queryPartitionStats);
    }

    /** Resolves the request body criteria DTO into the criteria object used by the service. */
    private static MailMessageCriteria criteriaOf(MailMessageQueryDTO query) {
        return new MailMessageCriteria(query != null ? query.criteria : null);
    }

    private static int limitOf(MailMessageQueryDTO query) {
        return (query != null && query.limit != null) ? query.limit : DEFAULT_LIMIT;
    }

}
