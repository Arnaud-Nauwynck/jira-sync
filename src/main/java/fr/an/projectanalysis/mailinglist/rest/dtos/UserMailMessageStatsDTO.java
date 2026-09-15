package fr.an.projectanalysis.mailinglist.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserMailMessageStatsDTO {

    public String user;

    public int messageCount;
    public int threadStartedCount;
    public int replyCount;

    public Map<String,UserMailMessagePerMonthStatsDTO> perMonth = new LinkedHashMap<>();

    public UserMailMessageStatsDTO(String user) {
        this.user = user;
    }

    public void add(String month, MailMessageDTO msg) {
        this.messageCount++;
        apply(analyze(msg));
        UserMailMessagePerMonthStatsDTO perUserPerMonth = this.perMonth.computeIfAbsent(
                month, UserMailMessagePerMonthStatsDTO::new);
        perUserPerMonth.add(msg);
    }

    private void apply(MessageOutcome o) {
        if (o.threadStart) {
            this.threadStartedCount++;
        } else {
            this.replyCount++;
        }
    }

    @NoArgsConstructor
    public static class UserMailMessagePerMonthStatsDTO {

        /** Number of Message-IDs to sample into {@link #firstMessages}. */
        private static final int FIRST_MESSAGES_SAMPLE_SIZE = 2;

        public String month;

        public int messageCount;
        public int threadStartedCount;
        public int replyCount;

        /** A sampling of the first {@value #FIRST_MESSAGES_SAMPLE_SIZE} Message-IDs added for this month. */
        public List<String> firstMessages = new ArrayList<>();

        public UserMailMessagePerMonthStatsDTO(String month) {
            this.month = month;
        }

        public void add(MailMessageDTO msg) {
            this.messageCount++;
            if (this.firstMessages.size() < FIRST_MESSAGES_SAMPLE_SIZE) {
                this.firstMessages.add(msg.messageId);
            }
            apply(analyze(msg));
        }

        private void apply(MessageOutcome o) {
            if (o.threadStart) {
                this.threadStartedCount++;
            } else {
                this.replyCount++;
            }
        }
    }

    /** Outcome of analyzing a single message, applied identically to the global and per-month counters. */
    private static final class MessageOutcome {
        final boolean threadStart;

        MessageOutcome(boolean threadStart) {
            this.threadStart = threadStart;
        }
    }

    private static MessageOutcome analyze(MailMessageDTO msg) {
        boolean threadStart = msg.inReplyTo == null || msg.inReplyTo.isBlank();
        return new MessageOutcome(threadStart);
    }

}
