package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** One user, as returned by the user search endpoints. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraUserDefDTO {
    public String self;
    public String key;
    public String name;
    public String displayName;
    public String emailAddress;
    public Boolean active;
    public Boolean deleted;
    public String timeZone;
    public String locale;
    public Map<String, String> avatarUrls;
}
