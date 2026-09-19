package fr.an.projectanalysis.mailinglist.service;

import fr.an.projectanalysis.mailinglist.rest.dtos.MailMessageDTO;
import fr.an.projectanalysis.rest.dtos.UserActivityStatsDTO;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Extracts the sent/replied/voted event of a single {@link MailMessageDTO} and adds it into the
 * per-user, per-month {@link UserActivityStatsDTO} accumulator, keyed by the sender (the raw
 * {@code From} header) and the message's archive month.
 */
public final class MailMessageActivityAnalyzer {

    private static final String UNKNOWN_USER = "unknown";

    /** Matches a "[VOTE]" tag anywhere in the subject (case-insensitive), as used on Apache-style lists. */
    private static final Pattern VOTE_SUBJECT_PATTERN = Pattern.compile("(?i)\\[VOTE]");

    private MailMessageActivityAnalyzer() {
    }

    public static void contribute(Map<String, UserActivityStatsDTO> acc, String month, MailMessageDTO msg) {
        String sender = (msg.from != null && !msg.from.isBlank()) ? msg.from : UNKNOWN_USER;
        UserActivityStatsDTO stats = acc.computeIfAbsent(sender, UserActivityStatsDTO::new);

        boolean isReply = msg.inReplyTo != null && !msg.inReplyTo.isBlank();
        if (!isReply) {
            stats.addMailMessageSent(month);
        } else if (msg.subject != null && VOTE_SUBJECT_PATTERN.matcher(msg.subject).find()) {
            stats.addMailMessageVoted(month);
        } else {
            stats.addMailMessageReplied(month);
        }
    }

}
