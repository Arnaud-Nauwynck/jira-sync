package fr.an.jira.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserIssueCreateStatsDTO {
    public String user;
    public int issueCreateCount;

    @NoArgsConstructor
    public static class UserIssueCreatePerYearStatsDTO {
        public int year;
        public int issueCreateCount;

        public UserIssueCreatePerYearStatsDTO(int year) {
            this.year = year;
        }
    }

    public Map<String,UserIssueCreatePerYearStatsDTO> perYear = new LinkedHashMap<>();

    public UserIssueCreateStatsDTO(String user) {
        this.user = user;
    }
}
