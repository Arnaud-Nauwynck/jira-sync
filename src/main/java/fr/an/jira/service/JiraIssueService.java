package fr.an.jira.service;

import fr.an.jira.repository.JiraIssueRepository;
import fr.an.jira.rest.dtos.UserIssueCreateStatsDTO;
import fr.an.jira.rest.dtos.UserIssueCreateStatsDTO.UserIssueCreatePerYearStatsDTO;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Slf4j
public class JiraIssueService {

    private static final String UNKNOWN_USER = "unknown";

    private final JiraIssueRepository repository;

    public JiraIssueService(JiraIssueRepository repository) {
        this.repository = repository;
    }

    public Collection<UserIssueCreateStatsDTO> queryUserIssueCreateStats(int fromYear, int toYear, String usernamePatternText) {
        Map<String,UserIssueCreateStatsDTO> tmp = new LinkedHashMap<>();
        Pattern usernamePattern = (usernamePatternText != null && !usernamePatternText.isBlank())? Pattern.compile(usernamePatternText) : null;
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            String user = creatorOf(issue);
            if (usernamePattern == null || usernamePattern.matcher(user).matches()) {
                UserIssueCreateStatsDTO statPerUser = tmp.computeIfAbsent(user, UserIssueCreateStatsDTO::new);
                statPerUser.issueCreateCount++;
                UserIssueCreatePerYearStatsDTO perUserPerYear = statPerUser.perYear.computeIfAbsent(Integer.toString(year), y -> new UserIssueCreatePerYearStatsDTO(Integer.parseInt(y)));
                perUserPerYear.issueCreateCount++;
            }
        });
        return tmp.values();
    }

    /** The issue creator's username, falling back to the reporter, then displayName, when missing. */
    private static String creatorOf(JsonNode issue) {
        JsonNode fields = issue.path("fields");
        JsonNode user = fields.path("creator");
        if (user.isMissingNode() || user.isNull()) {
            user = fields.path("reporter");
        }
        String name = user.path("name").asText(null);
        if (name == null || name.isBlank()) {
            name = user.path("displayName").asText(null);
        }
        return name != null && !name.isBlank() ? name : UNKNOWN_USER;
    }
}
