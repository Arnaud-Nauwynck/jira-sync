package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Response of "GET /rest/api/2/project/{key}/role/{id}". */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraProjectRoleDTO {
    public String self;
    public Long id;
    public String name;
    public String description;
    public List<SourceJiraRoleActorDTO> actors;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class SourceJiraRoleActorDTO {
        public Long id;
        public String displayName;
        public String type;
        public String name;
        public String avatarUrl;
    }
}
