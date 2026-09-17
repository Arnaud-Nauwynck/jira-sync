package fr.an.projectanalysis.mailinglist.service;

import fr.an.projectanalysis.mailinglist.repository.MailMessageRepository;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageAnnotationDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageCriteriaDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageExtraFieldsDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessagePartitionStatsDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageQueryDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.MonthCountDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.SenderCountDTO;
import fr.an.projectanalysis.mailinglist.rest.dtos.UserMailMessageStatsDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
        List<MailMessageDTO> result = new ArrayList<>();
        repository.scanMessages(fromMonth, toMonth, (month, msg) -> {
            if (result.size() >= limit) {
                return;
            }
            boolean matches = matches(fromPattern, msg.from) && matches(subjectPattern, msg.subject) && matches(bodyPattern, msg.bodyText)
                    && matchesCriteria(c, msg);
            if (matches) {
                result.add(msg);
            }
        });
        return result;
    }

    private static boolean matchesCriteria(MailMessageCriteriaDTO c, MailMessageDTO msg) {
        if (c == null) {
            return true;
        }
        if (!matchesAny(c.subjectContains, msg.subject)) {
            return false;
        }
        if (!matchesAny(c.bodyContains, msg.bodyText)) {
            return false;
        }
        if (!matchesAny(c.fromContains, msg.from)) {
            return false;
        }
        List<String> toCc = new ArrayList<>();
        if (msg.to != null) {
            toCc.addAll(msg.to);
        }
        if (msg.cc != null) {
            toCc.addAll(msg.cc);
        }
        if (!matchesAny(c.toCcContains, toCc.toArray(String[]::new))) {
            return false;
        }

        MailMessageExtraFieldsDTO annotated = msg.annotated;
        boolean hasAnalysis = annotated != null && annotated.analysisSummary != null && !annotated.analysisSummary.isBlank();
        if (!matchesAvailability(c.analysisAvailability, hasAnalysis)) {
            return false;
        }
        if (!matchesAny(c.analysisSummaryContains, annotated != null ? annotated.analysisSummary : null)) {
            return false;
        }
        if (!matchesDateRange(c.analysisSummaryUpdatedFrom, c.analysisSummaryUpdatedTo,
                annotated != null ? annotated.analysisSummaryLastUpdateTime : null)) {
            return false;
        }
        if (!matchesTokensRangeK(c.analysisSummaryMinTokensK, c.analysisSummaryMaxTokensK,
                annotated != null ? annotated.analysisSummaryTokensConsumed : 0)) {
            return false;
        }
        List<String> analysisExtraPrompts = annotated != null && annotated.analysisUserExtraPrompts != null
                ? annotated.analysisUserExtraPrompts : List.of();
        if (!matchesAny(c.analysisUserExtraPromptsContains, analysisExtraPrompts.toArray(String[]::new))) {
            return false;
        }

        boolean hasDevWork = annotated != null && annotated.developmentWorkDescribed != null && !annotated.developmentWorkDescribed.isBlank();
        if (!matchesAvailability(c.developmentWorkAvailability, hasDevWork)) {
            return false;
        }
        if (!matchesAny(c.developmentWorkDescribedContains, annotated != null ? annotated.developmentWorkDescribed : null)) {
            return false;
        }
        if (!matchesDateRange(c.developmentWorkUpdatedFrom, c.developmentWorkUpdatedTo,
                annotated != null ? annotated.developmentWorkLastUpdateTime : null)) {
            return false;
        }
        if (!matchesTokensRangeK(c.developmentWorkMinTokensK, c.developmentWorkMaxTokensK,
                annotated != null ? annotated.developmentWorkTokensConsumed : 0)) {
            return false;
        }
        List<String> devWorkExtraPrompts = annotated != null && annotated.developmentWorkUserExtraPrompts != null
                ? annotated.developmentWorkUserExtraPrompts : List.of();
        if (!matchesAny(c.developmentWorkUserExtraPromptsContains, devWorkExtraPrompts.toArray(String[]::new))) {
            return false;
        }

        boolean hasPersonalInterrest = annotated != null && annotated.personalInterrestComment != null && !annotated.personalInterrestComment.isBlank();
        if (!matchesAvailability(c.personalInterrestAvailability, hasPersonalInterrest)) {
            return false;
        }
        if (!matchesAny(c.personalInterrestCommentContains, annotated != null ? annotated.personalInterrestComment : null)) {
            return false;
        }
        if (!matchesNumberRange(c.personalInterrestMinPriority, c.personalInterrestMaxPriority,
                annotated != null ? annotated.personalInterrestPriority10 : null)) {
            return false;
        }
        return true;
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

    /** True when the CSV filter is blank, or at least one of the given values contains (case-insensitively) one of its comma-separated terms. */
    private static boolean matchesAny(String csvFilter, String... values) {
        List<String> terms = parseCsvList(csvFilter);
        if (terms.isEmpty()) {
            return true;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String lower = value.toLowerCase();
            for (String term : terms) {
                if (lower.contains(term.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> parseCsvList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** 'yes' requires present, 'no' requires absent, 'any'/blank/null does not filter. */
    private static boolean matchesAvailability(String availability, boolean present) {
        if ("yes".equals(availability)) {
            return present;
        }
        if ("no".equals(availability)) {
            return !present;
        }
        return true;
    }

    private static boolean matchesDateRange(String fromDate, String toDate, LocalDateTime value) {
        boolean hasFrom = fromDate != null && !fromDate.isBlank();
        boolean hasTo = toDate != null && !toDate.isBlank();
        if (!hasFrom && !hasTo) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (hasFrom && value.isBefore(LocalDate.parse(fromDate).atStartOfDay())) {
            return false;
        }
        if (hasTo && value.isAfter(LocalDate.parse(toDate).atTime(23, 59, 59))) {
            return false;
        }
        return true;
    }

    private static boolean matchesNumberRange(Integer min, Integer max, Integer value) {
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (min != null && value < min) {
            return false;
        }
        if (max != null && value > max) {
            return false;
        }
        return true;
    }

    /** min/max are expressed in kilo-tokens (thousands); value is the raw token count. */
    private static boolean matchesTokensRangeK(Integer minK, Integer maxK, int value) {
        if (minK == null && maxK == null) {
            return true;
        }
        if (minK != null && value < minK * 1000) {
            return false;
        }
        if (maxK != null && value > maxK * 1000) {
            return false;
        }
        return true;
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
