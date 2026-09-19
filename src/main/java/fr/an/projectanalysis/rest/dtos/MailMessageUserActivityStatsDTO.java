package fr.an.projectanalysis.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-user mailing-list activity stats, broken down per calendar month ("yyyy-MM") in
 * {@link #perMonth}. Built by {@code fr.an.projectanalysis.service.UserActivityStatsService},
 * fed by {@code MailMessageActivityAnalyzer}.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailMessageUserActivityStatsDTO {

    public String user;

    public int mailMessageSentCount;
    public int mailMessageRepliedCount;
    public int mailMessageVotedCount;

    public Map<String, MailMessageUserActivityMonthStatsDTO> perMonth = new LinkedHashMap<>();

    public MailMessageUserActivityStatsDTO(String user) {
        this.user = user;
    }

    public void addMailMessageSent(String month) { mailMessageSentCount++; perMonth(month).mailMessageSentCount++; }
    public void addMailMessageReplied(String month) { mailMessageRepliedCount++; perMonth(month).mailMessageRepliedCount++; }
    public void addMailMessageVoted(String month) { mailMessageVotedCount++; perMonth(month).mailMessageVotedCount++; }

    private MailMessageUserActivityMonthStatsDTO perMonth(String month) {
        return perMonth.computeIfAbsent(month, MailMessageUserActivityMonthStatsDTO::new);
    }

    /** Total count of all mailing-list activity metrics, for ranking users by mailing-list activity. */
    public int totalCount() {
        return mailMessageSentCount + mailMessageRepliedCount + mailMessageVotedCount;
    }

    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MailMessageUserActivityMonthStatsDTO {

        public String month;

        public int mailMessageSentCount;
        public int mailMessageRepliedCount;
        public int mailMessageVotedCount;

        public MailMessageUserActivityMonthStatsDTO(String month) {
            this.month = month;
        }
    }

}
