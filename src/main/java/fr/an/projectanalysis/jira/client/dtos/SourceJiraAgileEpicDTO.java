package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** One epic of "GET /rest/agile/1.0/board/{id}/epic". */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraAgileEpicDTO {
    public Long id;
    public String key;
    public String self;
    public String name;
    public String summary;
    public Boolean done;
    public SourceJiraAgileEpicColorDTO color;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraAgileEpicColorDTO {
        public String key;
    }
}
