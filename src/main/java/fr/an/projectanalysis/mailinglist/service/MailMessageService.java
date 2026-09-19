package fr.an.projectanalysis.mailinglist.service;

import fr.an.projectanalysis.mailinglist.repository.MailMessageRepository;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageAnnotationDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageCriteriaDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageExtraFieldsDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessagePartitionStatsDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageQueryDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MonthCountDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.NearbyMailMessagesDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.SenderCountDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.UserMailMessageStatsDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class MailMessageService {

    private static final String UNKNOWN_USER = "unknown";

    private static final int DEFAULT_LIMIT = 1000;

    private final MailMessageRepository repository;

    public MailMessageService(MailMessageRepository repository) {
        this.repository = repository;
    }

    /** Finds a single message by its Message-ID, or returns null if not found. */
    public MailMessageDTO findByMessageId(String messageId) {
        return repository.findByMessageId(messageId);
    }

    /**
     * For the message with the given Message-ID, finds the Message-IDs of the nearest earlier
     * ("prev") and later ("next") message, ordered by {@code Date}: the nearest message overall,
     * and the nearest one from the same sender (the raw {@code From} header).
     */
    public NearbyMailMessagesDTO findNearbyMessages(String messageId) {
        MailMessageDTO target = repository.getByMessageId(messageId);
        String sender = senderOf(target);

        List<MailMessageDTO> allMessages = repository.findAll().stream()
                .sorted(Comparator.comparing((MailMessageDTO msg) -> msg.date, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        int targetIndex = -1;
        for (int i = 0; i < allMessages.size(); i++) {
            if (messageId.equals(allMessages.get(i).messageId)) {
                targetIndex = i;
                break;
            }
        }

        NearbyMailMessagesDTO dto = new NearbyMailMessagesDTO();
        if (targetIndex < 0) {
            return dto;
        }
        for (int i = targetIndex - 1; i >= 0; i--) {
            MailMessageDTO msg = allMessages.get(i);
            if (dto.prevMail == null) {
                dto.prevMail = msg.messageId;
            }
            if (dto.prevMailSameSender == null && sender.equalsIgnoreCase(senderOf(msg))) {
                dto.prevMailSameSender = msg.messageId;
            }
            if (dto.prevMail != null && dto.prevMailSameSender != null) {
                break;
            }
        }
        for (int i = targetIndex + 1; i < allMessages.size(); i++) {
            MailMessageDTO msg = allMessages.get(i);
            if (dto.nextMail == null) {
                dto.nextMail = msg.messageId;
            }
            if (dto.nextMailSameSender == null && sender.equalsIgnoreCase(senderOf(msg))) {
                dto.nextMailSameSender = msg.messageId;
            }
            if (dto.nextMail != null && dto.nextMailSameSender != null) {
                break;
            }
        }
        return dto;
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

    /** Lists the messages matching the given criteria (Data Fetching + Main/Analysis/Development Work/Personal
     * Interest filter criteria of the mailing-list page), capped at {@code query.limit} (default 1000). */
    public List<MailMessageDTO> queryMessages(MailMessageQueryDTO query) {
        return queryMessagesMatching(query != null ? query.criteria : null, limitOf(query));
    }

    /** Same as {@link #queryMessages(MailMessageQueryDTO)}, but returns only the message ids. */
    public List<String> queryMessageIds(MailMessageQueryDTO query) {
        List<MailMessageDTO> matched = queryMessagesMatching(query != null ? query.criteria : null, limitOf(query));
        List<String> ids = new ArrayList<>(matched.size());
        for (MailMessageDTO msg : matched) {
            ids.add(msg.messageId);
        }
        return ids;
    }

    private static int limitOf(MailMessageQueryDTO query) {
        return (query != null && query.limit != null) ? query.limit : DEFAULT_LIMIT;
    }

    private List<MailMessageDTO> queryMessagesMatching(MailMessageCriteriaDTO c, int limit) {
        String fromMonth = c != null ? c.fromMonth : null;
        String toMonth = c != null ? c.toMonth : null;
        Pattern fromPattern = c != null ? compileOrNull(c.fromPattern) : null;
        Pattern subjectPattern = c != null ? compileOrNull(c.subjectPattern) : null;
        Pattern bodyPattern = c != null ? compileOrNull(c.bodyPattern) : null;
        MailMessageCriteria messageCriteria = new MailMessageCriteria(c);
        List<MailMessageDTO> result = new ArrayList<>();
        // partition pruning: scan from the most recent partition (toMonth) backwards, stopping as
        // soon as the limit is reached, so older partitions are never loaded once satisfied.
        repository.scanMessagesFromMostRecent(fromMonth, toMonth, (month, msg) -> {
            boolean matches = matches(fromPattern, msg.from) && matches(subjectPattern, msg.subject) && matches(bodyPattern, msg.bodyText)
                    && messageCriteria.test(msg);
            if (matches) {
                result.add(msg);
            }
            return result.size() < limit;
        });
        return result;
    }

    /** Count of locally-synced messages per "archived" (month) partition. */
    public MailMessagePartitionStatsDTO queryPartitionStats() {
        MailMessagePartitionStatsDTO dto = new MailMessagePartitionStatsDTO();
        List<MonthCountDTO> monthStats = new ArrayList<>();
        for (Map.Entry<String, Integer> e : repository.countByPartitionMonth().entrySet()) {
            monthStats.add(new MonthCountDTO(e.getKey(), e.getValue()));
        }
        dto.statsPerMonth = monthStats;
        List<SenderCountDTO> senderStats = new ArrayList<>();
        for (Map.Entry<String, MailMessageRepository.SenderStats> e : repository.senderStats().entrySet()) {
            senderStats.add(e.getValue().toDTO(e.getKey()));
        }
        dto.statsPerSender = senderStats;
        return dto;
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

    /** Lists the annotations of messages archived between fromMonth and toMonth ("yyyy-MM", inclusive), skipping un-annotated ones. */
    public List<MailMessageAnnotationDTO> listMessageAnnotations(String fromMonth, String toMonth) {
        List<MailMessageAnnotationDTO> res = new ArrayList<>();
        repository.scanMessages(fromMonth, toMonth, (month, msg) -> {
            MailMessageExtraFieldsDTO annotated = msg.annotated;
            if (annotated != null) {
                res.add(new MailMessageAnnotationDTO(msg.messageId, annotated));
            }
        });
        return res;
    }

    public void putAnnotation(String messageId, MailMessageExtraFieldsDTO annotated) {
        repository.putAnnotation(messageId, annotated);
    }

    public void putPersonalInterrestComment(String messageId, String personalInterrestComment, Integer personalInterrestPriority10) {
        MailMessageDTO msg = repository.getByMessageId(messageId);
        MailMessageExtraFieldsDTO annotated = msg.annotatedOrCreate();
        annotated.personalInterrestComment = personalInterrestComment;
        annotated.personalInterrestPriority10 = personalInterrestPriority10;
        repository.putAnnotation(messageId, annotated);
    }

    public void removeAnnotation(String messageId) {
        repository.removeAnnotation(messageId);
    }
}
