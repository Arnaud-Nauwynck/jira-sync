package fr.an.jira.service;

import fr.an.jira.repository.JiraIssueRepository;
import fr.an.jira.rest.dtos.AnnotatedJiraIssueDTO;
import fr.an.jira.rest.dtos.UserIssueCreateStatsDTO;
import fr.an.jira.rest.dtos.UserIssueCreateStatsDTO.UserIssueCreatePerYearStatsDTO;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Slf4j
public class JiraIssueService {

    private static final String UNKNOWN_USER = "unknown";

    private final JiraIssueRepository repository;

    private final ObjectMapper mapper;

    public JiraIssueService(JiraIssueRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
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

    /** Finds a single issue by its key, or returns null if not found. */
    public AnnotatedJiraIssueDTO findAnnotatedIssueByKey(String key) {
        JsonNode issue = repository.findByKey(key);
        return issue != null ? mapper.treeToValue(issue, AnnotatedJiraIssueDTO.class) : null;
    }

    /** Lists the issues created between fromYear and toYear (inclusive), optionally filtered by creator username. */
    public List<AnnotatedJiraIssueDTO> queryAnnotatedIssues(int fromYear, int toYear, String usernamePatternText) {
        List<AnnotatedJiraIssueDTO> result = new ArrayList<>();
        Pattern usernamePattern = (usernamePatternText != null && !usernamePatternText.isBlank())? Pattern.compile(usernamePatternText) : null;
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            if (usernamePattern == null || usernamePattern.matcher(creatorOf(issue)).matches()) {
                result.add(mapper.treeToValue(issue, AnnotatedJiraIssueDTO.class));
            }
        });
        return result;
    }

    /** The issue creator's username, falling back to the reporter, when missing. */
    private static String creatorOf(JsonNode issue) {
        JsonNode fields = issue.path("fields");
        String name = fields.path("creator").asText(null);
        if (name == null || name.isBlank()) {
            name = fields.path("reporter").asText(null);
        }
        return name != null && !name.isBlank() ? name : UNKNOWN_USER;
    }
}
