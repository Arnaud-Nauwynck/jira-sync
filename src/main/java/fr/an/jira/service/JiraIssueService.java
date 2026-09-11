package fr.an.jira.service;

import fr.an.jira.repository.JiraIssueRepository;
import fr.an.jira.rest.dtos.JiraIssueAnnotationDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO;
import fr.an.jira.rest.dtos.UserIssueCreateStatsDTO;
import fr.an.jira.rest.dtos.UserIssueCreateStatsDTO.UserIssueCreatePerYearStatsDTO;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;

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

    /** Finds a single issue by its key, or returns null if not found. */
    public JiraIssueDTO findAnnotatedIssueByKey(String key) {
        return repository.findByKey(key);
    }

    /** Lists the issues created between fromYear and toYear (inclusive), optionally filtered by creator username. */
    public List<JiraIssueDTO> queryAnnotatedIssues(int fromYear, int toYear, String usernamePatternText) {
        List<JiraIssueDTO> result = new ArrayList<>();
        Pattern usernamePattern = (usernamePatternText != null && !usernamePatternText.isBlank())? Pattern.compile(usernamePatternText) : null;
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            if (usernamePattern == null || usernamePattern.matcher(creatorOf(issue)).matches()) {
                result.add(issue);
            }
        });
        return result;
    }

    /** The issue creator's username, falling back to the reporter, when missing. */
    private static String creatorOf(JiraIssueDTO issue) {
        String name = issue.fields != null ? issue.fields.creator : null;
        if (name == null || name.isBlank()) {
            name = issue.fields != null ? issue.fields.reporter : null;
        }
        return name != null && !name.isBlank() ? name : UNKNOWN_USER;
    }

    public List<JiraIssueAnnotationDTO> listIssueAnnotations(int fromYear, int toYear) {
        val res = new ArrayList<JiraIssueAnnotationDTO>();
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            val annotation = issue.getAnnotated();
            if (annotation != null) {
                res.add(new JiraIssueAnnotationDTO(issue.key, annotation));
            }
        });
        return res;
    }

    public JiraIssueDTO getByKey(String key) {
        return repository.getByKey(key);
    }

    public void putAnnotation(String key, JiraIssueDTO.IssueExtraFieldsDTO annotated) {
        repository.putAnnotation(key, annotated);
    }

    public void removeAnnotation(String key) {
        repository.removeAnnotation(key);
    }

}
