package fr.an.jira.mailinglist.service;

import fr.an.jira.mailinglist.client.MailingListApiClient;
import fr.an.jira.mailinglist.client.MboxMessageSplitter;
import fr.an.jira.mailinglist.configuration.MailingListSyncProperties;
import fr.an.jira.mailinglist.mapper.MimeMessageToMailMessageMapper;
import fr.an.jira.mailinglist.repository.MailMessageRepository;
import fr.an.jira.mailinglist.rest.dtos.MailMessageDTO;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.MimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Sync the Apache mailing-list archive (one monthly mbox per {@code list}/{@code domain}, see
 * {@link MailingListSyncProperties}) to local JSON files.
 * - fetches one full month's mbox per iteration (the archive API has no per-message "since" filter)
 * - 429/5xx backoff + politeness delay (see {@link MailingListApiClient})
 * - incremental mode: months already fully archived (i.e. strictly before the current month at the
 *   time they were last fetched) are never re-fetched; the current (still-filling) month is always
 *   re-fetched, tracked in a local "mailing-list-sync-state.json" file
 */
@Component
public class MailingListSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(MailingListSyncRunner.class);

    private final MailingListSyncProperties props;

    private final MailingListApiClient apiClient;

    private final MailMessageRepository repository;

    private final ObjectMapper mapper;

    private final Path syncStateFile;

    private final long syncDelayMs;

    public MailingListSyncRunner(MailingListSyncProperties props, MailingListApiClient apiClient,
                                  MailMessageRepository repository, ObjectMapper mapper) {
        this.props = props;
        this.apiClient = apiClient;
        this.repository = repository;
        this.mapper = mapper;
        this.syncStateFile = Path.of(props.getMailingListSyncLocalDir(), "mailing-list-sync-state.json");
        this.syncDelayMs = props.getDelayMs();
    }

    /** Full or incremental sync of all archived messages for the configured list/domain. */
    public void syncAll() throws Exception {
        long startMillis = System.currentTimeMillis();
        YearMonth currentMonth = YearMonth.now();
        YearMonth lastClosedMonth = loadLastClosedMonth();
        YearMonth fromMonth = (lastClosedMonth != null)
                ? lastClosedMonth.plusMonths(1)
                : YearMonth.parse(props.getStartYearMonth());
        if (fromMonth.isAfter(currentMonth)) {
            fromMonth = currentMonth;
        }

        if (lastClosedMonth != null) {
            log.info("incremental sync: fetching months {} .. {}", fromMonth, currentMonth);
        } else {
            log.info("full sync: fetching months {} .. {}", fromMonth, currentMonth);
        }

        int messageChangeCount = 0;
        for (YearMonth month = fromMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            String yearMonth = month.toString();
            byte[] mboxBytes = apiClient.fetchMbox(yearMonth);
            List<byte[]> rawMessages = MboxMessageSplitter.splitMessages(mboxBytes);

            List<MailMessageDTO> partitionedItems = new ArrayList<>(rawMessages.size());
            for (byte[] raw : rawMessages) {
                try {
                    Message mimeMessage = Message.Builder.of()
                            .use(MimeConfig.PERMISSIVE)
                            .parse(new ByteArrayInputStream(raw))
                            .build();
                    MailMessageDTO dto = MimeMessageToMailMessageMapper.from(mimeMessage);
                    if (dto.messageId == null || dto.messageId.isBlank()) {
                        log.warn("  {} : skipping a message with no Message-ID", yearMonth);
                        continue;
                    }
                    partitionedItems.add(dto);
                    messageChangeCount++;
                } catch (Exception e) {
                    log.warn("  {} : failed to parse a message, skipping: {}", yearMonth, e.toString());
                }
            }

            repository.savePartitionData(yearMonth, partitionedItems);

            log.info("synced {} : {} messages", yearMonth, rawMessages.size());
            sleep(syncDelayMs);
        }

        saveLastClosedMonth(currentMonth.minusMonths(1));
        if (messageChangeCount > 0) {
            repository.compactAll();
        }

        int millis = (int) (System.currentTimeMillis() - startMillis);
        log.info("done syncAll, saved {} changes, took {} ms", messageChangeCount, millis);
    }

    /** Reads the last month considered fully closed (no longer re-fetched), or null if none / unreadable. */
    public YearMonth loadLastClosedMonth() {
        if (!Files.exists(syncStateFile)) {
            return null;
        }
        try {
            JsonNode state = mapper.readTree(syncStateFile.toFile());
            String s = state.path("lastClosedMonth").asText(null);
            return s != null ? YearMonth.parse(s) : null;
        } catch (Exception e) {
            log.warn("failed to read sync state file {}, falling back to full sync: {}",
                    syncStateFile, e.toString());
            return null;
        }
    }

    /** Records the last month considered fully closed; the current month is always re-fetched next run. */
    private void saveLastClosedMonth(YearMonth lastClosedMonth) throws Exception {
        ObjectNode state = mapper.createObjectNode();
        state.put("lastClosedMonth", lastClosedMonth.toString());
        Files.createDirectories(Path.of(props.getMailingListSyncLocalDir()));
        Files.writeString(syncStateFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
