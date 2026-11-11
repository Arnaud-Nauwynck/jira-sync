package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** One board of "GET /rest/agile/1.0/board". */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraAgileBoardDTO {
    public Long id;
    public String self;
    public String name;
    public String type;
}
