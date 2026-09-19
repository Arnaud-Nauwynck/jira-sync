package fr.an.projectanalysis.mailinglist.service;

import fr.an.projectanalysis.mailinglist.repository.MailMessageRepository;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageAnnotationDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageCompareIdsResultDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageExtraFieldsDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageIdAndLastUpdateTimeDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessagePartitionStatsDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MonthCountDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.NearbyMailMessagesDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.SenderCountDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.UserMailMessageStatsDTO;
import fr.an.projectanalysis.rest.dtos.UserActivityStatsDTO;
import fr.an.projectanalysis.util.CompareIdsUtils;
import fr.an.projectanalysis.util.CompareIdsUtils.CompareIdsResult;
import fr.an.projectanalysis.util.CritUtils;
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

    private final MailMessageRepository repository;

    public MailMessageService(MailMessageRepository repository) {
        this.repository = repository;
    }

    /** Finds a single message by its Message-ID, or returns null if not found. */
    public MailMessageDTO findByMessageId(String messageId) {
        return repository.findByMessageId(messageId);
    }

    /** Finds the messages having the given Message-IDs ("ids"), in the requested order; ids not found locally
     * are skipped. */
    public List<MailMessageDTO> findByIds(Collection<String> ids) {
        List<MailMessageDTO> res = new ArrayList<>(ids.size());
        for (String id : ids) {
            MailMessageDTO found = repository.findByMessageId(id);
            if (found != null) {
                res.add(found);
            }
        }
        return res;
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
            if (target.messageId.equals(allMessages.get(i).messageId)) {
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
                dto.prevMail = msg.id();
            }
            if (dto.prevMailSameSender == null && sender.equalsIgnoreCase(senderOf(msg))) {
                dto.prevMailSameSender = msg.id();
            }
            if (dto.prevMail != null && dto.prevMailSameSender != null) {
                break;
            }
        }
        for (int i = targetIndex + 1; i < allMessages.size(); i++) {
            MailMessageDTO msg = allMessages.get(i);
            if (dto.nextMail == null) {
                dto.nextMail = msg.id();
            }
            if (dto.nextMailSameSender == null && sender.equalsIgnoreCase(senderOf(msg))) {
                dto.nextMailSameSender = msg.id();
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
        MailMessageCriteria messageCriteria = MailMessageCriteria.ofPatterns(fromPatternText, subjectPatternText, bodyPatternText);
        repository.scanMessages(fromMonth, toMonth, (month, msg) -> {
            if (messageCriteria.test(msg)) {
                result.add(msg);
            }
        });
        return result;
    }

    /** Lists the messages matching the given criteria (Data Fetching + Main/Analysis/Development Work/Personal
     * Interest filter criteria of the mailing-list page), capped at {@code limit}. */
    public List<MailMessageDTO> queryMessages(MailMessageCriteria messageCriteria, int limit) {
        List<MailMessageDTO> result = new ArrayList<>();
        // partition pruning: scan from the most recent partition (toMonth) backwards, stopping as
        // soon as the limit is reached, so older partitions are never loaded once satisfied.
        repository.scanMessagesFromMostRecent(messageCriteria.getFromMonth(), messageCriteria.getToMonth(), (month, msg) -> {
            if (messageCriteria.test(msg)) {
                result.add(msg);
            }
            return result.size() < limit;
        });
        return result;
    }

    /** Same as {@link #queryMessages(MailMessageCriteria, int)}, but returns only the message ids. */
    public List<String> queryMessageIds(MailMessageCriteria messageCriteria, int limit) {
        List<MailMessageDTO> matched = queryMessages(messageCriteria, limit);
        List<String> ids = new ArrayList<>(matched.size());
        for (MailMessageDTO msg : matched) {
            ids.add(msg.id());
        }
        return ids;
    }

    /** Same as {@link #queryMessageIds(MailMessageCriteria, int)}, but returns for each message its Message-ID
     * with its last update time, that is its sent date, in epoch milliseconds. */
    public List<MailMessageIdAndLastUpdateTimeDTO> queryMessageIdAndLastUpdateTimes(MailMessageCriteria messageCriteria, int limit) {
        List<MailMessageDTO> matched = queryMessages(messageCriteria, limit);
        List<MailMessageIdAndLastUpdateTimeDTO> res = new ArrayList<>(matched.size());
        for (MailMessageDTO msg : matched) {
            long time = (msg.date != null) ? msg.date.toInstant().toEpochMilli() : 0L;
            res.add(new MailMessageIdAndLastUpdateTimeDTO(msg.id(), time));
        }
        return res;
    }

    /**
     * Compares the Message-IDs matched by 2 independent criteria: the ids matched by the left criteria only,
     * by both ("common"), and by the right criteria only. The common ids are only counted, unless
     * {@code fillCommonIds} is set, in which case they are also listed. Each side is capped at its own
     * limit, as in {@link #queryMessageIds(MailMessageCriteria, int)}.
     */
    public MailMessageCompareIdsResultDTO compareQueryIds(
            MailMessageCriteria leftCriteria, int leftLimit,
            MailMessageCriteria rightCriteria, int rightLimit,
            boolean fillCommonIds) {
        List<String> leftIds = queryMessageIds(leftCriteria, leftLimit);
        List<String> rightIds = queryMessageIds(rightCriteria, rightLimit);
        CompareIdsResult<String> compared = CompareIdsUtils.compareIds(leftIds, rightIds, fillCommonIds);

        MailMessageCompareIdsResultDTO res = new MailMessageCompareIdsResultDTO();
        res.leftOnlyIds = compared.leftOnlyIds;
        res.commonIds = compared.commonIds;
        res.commonCount = compared.commonCount;
        res.rightOnlyIds = compared.rightOnlyIds;
        return res;
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

    /**
     * Counts messages per sender (the raw {@code From} header), for messages archived between
     * fromYear and toYear (inclusive), optionally filtered by a regex matched (full match) against
     * the From header.
     */
    public Collection<UserMailMessageStatsDTO> queryUserMessageStats(
            int fromYear, int toYear, String fromPatternText
    ) {
        Map<String, UserMailMessageStatsDTO> tmp = new LinkedHashMap<>();
        Pattern fromPattern = CritUtils.compilePattern(fromPatternText);
        String fromMonth = fromYear + "-01";
        String toMonth = toYear + "-12";
        repository.scanMessages(fromMonth, toMonth, (month, msg) -> {
            String user = senderOf(msg);
            if (!CritUtils.matchesRegex(fromPattern, user)) {
                return;
            }
            UserMailMessageStatsDTO statPerUser = tmp.computeIfAbsent(user, UserMailMessageStatsDTO::new);
            statPerUser.add(month, msg);
        });
        return tmp.values();
    }

    /** Adds the sent/replied/voted event of the messages archived between fromMonth and toMonth ("yyyy-MM",
     * inclusive) into {@code acc}, keyed by the sender and the message's archive month. */
    public void contributeUserActivityStats(Map<String, UserActivityStatsDTO> acc, String fromMonth, String toMonth) {
        repository.scanMessages(fromMonth, toMonth, (month, msg) -> MailMessageActivityAnalyzer.contribute(acc, month, msg));
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
                res.add(new MailMessageAnnotationDTO(msg.id(), annotated));
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
