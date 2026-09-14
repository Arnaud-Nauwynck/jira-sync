package fr.an.jira.mailinglist.service;

import fr.an.jira.mailinglist.repository.MailMessageRepository;
import fr.an.jira.mailinglist.rest.dtos.MailMessageDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class MailMessageService {

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
}
