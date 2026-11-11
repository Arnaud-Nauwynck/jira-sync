package fr.an.projectanalysis.jira.rest.converter;

import fr.an.projectanalysis.jira.client.dtos.SourceJiraProjectVersionDTO;
import fr.an.projectanalysis.jira.rest.dtos.JiraProjectVersionDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Converts SourceJiraProjectVersionDTO to JiraProjectVersionDTO */
@Component
public class JiraProjectVersionDTOConverter {

    public JiraProjectVersionDTO toDTO(SourceJiraProjectVersionDTO src) {
        JiraProjectVersionDTO res = new JiraProjectVersionDTO();
        res.id = src.id;
        res.name = src.name;
        res.description = src.description;
        res.archived = src.archived;
        res.released = src.released;
        res.overdue = src.overdue;
        res.startDate = parseDate(src.startDate);
        res.releaseDate = parseDate(src.releaseDate);
        return res;
    }

    public List<JiraProjectVersionDTO> toDTOs(List<SourceJiraProjectVersionDTO> srcs) {
        List<JiraProjectVersionDTO> res = new ArrayList<>();
        if (srcs != null) {
            for (SourceJiraProjectVersionDTO src : srcs) {
                res.add(toDTO(src));
            }
        }
        return res;
    }

    private static LocalDate parseDate(String text) {
        return (text == null || text.isBlank()) ? null : LocalDate.parse(text);
    }
}
