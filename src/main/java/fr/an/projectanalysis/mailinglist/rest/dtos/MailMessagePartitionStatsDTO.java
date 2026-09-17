package fr.an.projectanalysis.mailinglist.rest.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** Count of locally-synced mailing-list messages per "archived" (month) partition, and per sender. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailMessagePartitionStatsDTO {

    public List<MonthCountDTO> statsPerMonth;

    public List<SenderCountDTO> statsPerSender;

}
