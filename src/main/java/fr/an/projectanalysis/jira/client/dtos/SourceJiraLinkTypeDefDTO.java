package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** One issue link type ("Blocks", "Duplicate", ...). */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraLinkTypeDefDTO {
    public String id;
    public String name;
    public String inward;
    public String outward;
    public String self;
}
