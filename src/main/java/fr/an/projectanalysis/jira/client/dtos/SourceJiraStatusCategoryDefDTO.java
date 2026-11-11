package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** One element of "GET /rest/api/2/statuscategory". */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraStatusCategoryDefDTO {
    public String self;
    public Long id;
    public String key;
    public String colorName;
    public String name;
}
