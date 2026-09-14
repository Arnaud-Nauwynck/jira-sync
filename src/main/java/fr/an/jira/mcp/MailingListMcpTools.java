package fr.an.jira.mcp;

import fr.an.jira.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.jira.mailinglist.service.MailMessageService;
import fr.an.jira.mailinglist.service.MailingListSyncRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP tools exposing the local mailing-list archive mirror ({@link MailMessageService},
 * {@link MailingListSyncRunner}).
 */
@Component
@Slf4j
public class MailingListMcpTools {

    private final MailMessageService messageService;
    private final MailingListSyncRunner mailingListSyncRunner;

    public MailingListMcpTools(MailMessageService messageService, MailingListSyncRunner mailingListSyncRunner) {
        this.messageService = messageService;
        this.mailingListSyncRunner = mailingListSyncRunner;
    }

    @Tool(description = "Find a single locally-synced mailing-list message by its Message-ID. Returns null if not found.")
    public MailMessageDTO findMessageByMessageId(
            @ToolParam(description = "Message-ID, e.g. CAMFhwAa2Eh1baeKP_-kh0oug8NGdPog8ivKtMsEqwUqHN_jTMg@mail.gmail.com") String messageId) {
        log.info("mcp tool findMessageByMessageId({})", messageId);
        return messageService.findByMessageId(messageId);
    }

    @Tool(description = "List locally-synced mailing-list messages archived between fromMonth and toMonth (both 'yyyy-MM', inclusive), optionally filtered by regexes matched against the From header, the Subject, and/or the body text")
    public List<MailMessageDTO> queryMessages(
            @ToolParam(description = "Earliest archived month, inclusive, format yyyy-MM") String fromMonth,
            @ToolParam(description = "Latest archived month, inclusive, format yyyy-MM") String toMonth,
            @ToolParam(description = "Optional regex matched against the From header (name and/or email)", required = false) String fromPattern,
            @ToolParam(description = "Optional regex matched against the Subject", required = false) String subjectPattern,
            @ToolParam(description = "Optional regex matched against the body text", required = false) String bodyPattern) {
        log.info("mcp tool queryMessages(fromMonth={}, toMonth={}, fromPattern={}, subjectPattern={}, bodyPattern={})",
                fromMonth, toMonth, fromPattern, subjectPattern, bodyPattern);
        return messageService.queryMessages(fromMonth, toMonth, fromPattern, subjectPattern, bodyPattern);
    }

    @Tool(description = "Run the mailing-list synchronization for the configured list/domain, pulling new/updated monthly mbox archives from lists.apache.org into the local mirror.")
    public String runMailingListSyncAll() throws Exception {
        log.info("mcp tool runMailingListSyncAll()");
        long startTime = System.currentTimeMillis();
        mailingListSyncRunner.syncAll();
        int millis = (int) (System.currentTimeMillis() - startTime);
        return "sync completed in " + millis + " ms";
    }

}
