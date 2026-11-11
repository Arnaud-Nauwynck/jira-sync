package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** One element of "GET /rest/api/2/field" (maps customfield_XXXXX ids to names). */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraFieldDefDTO {
    public String id;
    public String key;
    public String name;
    public Boolean custom;
    public Boolean orderable;
    public Boolean navigable;
    public Boolean searchable;
    public List<String> clauseNames;
    public SourceJiraFieldSchemaDTO schema;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraFieldSchemaDTO {
        public String type;
        public String items;
        public String system;
        public String custom;
        public Long customId;
    }
}
