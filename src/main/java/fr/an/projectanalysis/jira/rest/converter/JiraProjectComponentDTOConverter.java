package fr.an.projectanalysis.jira.rest.converter;

import fr.an.projectanalysis.jira.client.dtos.SourceJiraProjectComponentDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraProjectComponentDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Converts SourceJiraProjectComponentDTO to JiraProjectComponentDTO */
@Component
public class JiraProjectComponentDTOConverter {

    public JiraProjectComponentDTO toDTO(SourceJiraProjectComponentDTO src) {
        JiraProjectComponentDTO res = new JiraProjectComponentDTO();
        res.id = src.id;
        res.name = src.name;
        res.description = src.description;
        return res;
    }

    public List<JiraProjectComponentDTO> toDTOs(List<SourceJiraProjectComponentDTO> srcs) {
        List<JiraProjectComponentDTO> res = new ArrayList<>();
        if (srcs != null) {
            for (SourceJiraProjectComponentDTO src : srcs) {
                res.add(toDTO(src));
            }
        }
        return res;
    }
}
