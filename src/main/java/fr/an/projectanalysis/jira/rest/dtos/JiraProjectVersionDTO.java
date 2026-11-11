package fr.an.projectanalysis.jira.rest.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;

/** DTO corresponding to SourceJiraProjectVersionDTO */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class JiraProjectVersionDTO {
    public String id;
    public String name;
    public String description;
    public Boolean archived;
    public Boolean released;
    public Boolean overdue;
    public LocalDate startDate;
    public LocalDate releaseDate;
}
