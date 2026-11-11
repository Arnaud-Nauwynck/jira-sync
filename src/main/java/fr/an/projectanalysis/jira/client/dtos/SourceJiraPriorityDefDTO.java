package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** One element of "GET /rest/api/2/priority". */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraPriorityDefDTO {
    public String self;
    public String id;
    public String name;
    public String description;
    public String iconUrl;
    public String statusColor;
}
