package fr.an.jira.mailinglist.service;

import fr.an.jira.mailinglist.repository.MailMessageRepository;
import fr.an.jira.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.jira.mailinglist.rest.dtos.UserMailMessageStatsDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class MailMessageService {

    private static final String UNKNOWN_USER = "unknown";

    private final MailMessageRepository repository;

    public MailMessageService(MailMessageRepository repository) {
        this.repository = repository;
    }

    /** Finds a single message by its Message-ID, or returns null if not found. */
    public MailMessageDTO findByMessageId(String messageId) {
        return repository.findByMessageId(messageId);
    }

    /**
     * Lists the messages archived between fromMonth and toMonth ("yyyy-MM", inclusive), optionally
     * filtered by regexes matched against the From header, the Subject, and/or the body text (all
     * optional, combined with AND when more than one is given).
     */
    public List<MailMessageDTO> queryMessages(
            String fromMonth, String toMonth,
            String fromPatternText, String subjectPatternText, String bodyPatternText
    ) {
        List<MailMessageDTO> result = new ArrayList<>();
        Pattern fromPattern = compileOrNull(fromPatternText);
        Pattern subjectPattern = compileOrNull(subjectPatternText);
        Pattern bodyPattern = compileOrNull(bodyPatternText);
        repository.scanMessages(fromMonth, toMonth, (month, msg) -> {
            if (matches(fromPattern, msg.from) && matches(subjectPattern, msg.subject) && matches(bodyPattern, msg.bodyText)) {
                result.add(msg);
            }
        });
        return result;
    }

    private static Pattern compileOrNull(String patternText) {
        return (patternText != null && !patternText.isBlank()) ? Pattern.compile(patternText) : null;
    }

    private static boolean matches(Pattern pattern, String value) {
        return pattern == null || (value != null && pattern.matcher(value).find());
    }

    /**
     * Counts messages per sender (the raw {@code From} header), for messages archived between
     * fromYear and toYear (inclusive), optionally filtered by a regex matched (full match) against
     * the From header.
     */
    public Collection<UserMailMessageStatsDTO> queryUserMessageStats(
            int fromYear, int toYear, String fromPatternText
    ) {
        Map<String, UserMailMessageStatsDTO> tmp = new LinkedHashMap<>();
        Pattern fromPattern = compileOrNull(fromPatternText);
        String fromMonth = fromYear + "-01";
        String toMonth = toYear + "-12";
        repository.scanMessages(fromMonth, toMonth, (month, msg) -> {
            String user = senderOf(msg);
            if (fromPattern != null && !fromPattern.matcher(user).matches()) {
                return;
            }
            UserMailMessageStatsDTO statPerUser = tmp.computeIfAbsent(user, UserMailMessageStatsDTO::new);
            statPerUser.add(month, msg);
        });
        return tmp.values();
    }

    private static String senderOf(MailMessageDTO msg) {
        String from = msg.from;
        return from != null && !from.isBlank() ? from : UNKNOWN_USER;
    }
}
