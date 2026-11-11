package fr.an.projectanalysis.jira.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Response of "GET /rest/api/2/issueLinkType" (wrapped list). */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SourceJiraLinkTypesResponseDTO {
    public List<SourceJiraLinkTypeDefDTO> issueLinkTypes;
}
