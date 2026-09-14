package fr.an.jira.service;

import fr.an.jira.repository.JiraIssueRepository;
import fr.an.jira.rest.dtos.IssueExtraFieldsDTO;
import fr.an.jira.rest.dtos.JiraIssueAnnotationDTO;
import fr.an.jira.rest.dtos.JiraIssueDTO;
import fr.an.jira.rest.dtos.UserJiraIssueStatsDTO;
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

    public Collection<UserJiraIssueStatsDTO> queryUserIssueStats(
            int fromYear, int toYear,
            String usernamePatternText,
            String summaryPatternText,
            String descriptionPatternText,
            String commentPatternText,
            String commentAuthorPatternText
    ) {
        Map<String, UserJiraIssueStatsDTO> tmp = new LinkedHashMap<>();
        Pattern usernamePattern = compilePattern(usernamePatternText);
        Pattern summaryPattern = compilePattern(summaryPatternText);
        Pattern descriptionPattern = compilePattern(descriptionPatternText);
        Pattern commentPattern = compilePattern(commentPatternText);
        Pattern commentAuthorPattern = compilePattern(commentAuthorPatternText);
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            String user = creatorOf(issue);
            if (usernamePattern != null && !usernamePattern.matcher(user).matches()) {
                return;
            }
            if (!matchesText(summaryPattern, issue.fields != null ? issue.fields.summary : null)) {
                return;
            }
            if (!matchesText(descriptionPattern, issue.fields != null ? issue.fields.description : null)) {
                return;
            }
            if (!matchesComments(commentPattern, commentAuthorPattern, issue)) {
                return;
            }
            UserJiraIssueStatsDTO statPerUser = tmp.computeIfAbsent(user, UserJiraIssueStatsDTO::new);
            statPerUser.add(year, issue);
        });
        return tmp.values();
    }

    private static Pattern compilePattern(String patternText) {
        return (patternText != null && !patternText.isBlank()) ? Pattern.compile(patternText) : null;
    }

    private static boolean matchesText(Pattern pattern, String text) {
        return pattern == null || (text != null && pattern.matcher(text).find());
    }

    /** True when neither pattern is set, or the issue has at least one comment matching both given patterns. */
    private static boolean matchesComments(Pattern commentPattern, Pattern commentAuthorPattern, JiraIssueDTO issue) {
        if (commentPattern == null && commentAuthorPattern == null) {
            return true;
        }
        List<JiraIssueDTO.IssueCommentDTO> comments = issue.fields != null ? issue.fields.comments : null;
        if (comments == null) {
            return false;
        }
        for (JiraIssueDTO.IssueCommentDTO comment : comments) {
            boolean bodyMatches = commentPattern == null || (comment.body != null && commentPattern.matcher(comment.body).find());
            boolean authorMatches = commentAuthorPattern == null || (comment.author != null && commentAuthorPattern.matcher(comment.author).matches());
            if (bodyMatches && authorMatches) {
                return true;
            }
        }
        return false;
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

    public void putAnnotation(String key, IssueExtraFieldsDTO annotated) {
        repository.putAnnotation(key, annotated);
    }

    public void putPersonalInterrestComment(String key, String personalInterrestComment, Integer personalInterrestPriority10) {
        JiraIssueDTO issue = repository.getByKey(key);
        IssueExtraFieldsDTO annotated = issue.getAnnotated();
        if (annotated == null) {
            annotated = new IssueExtraFieldsDTO();
        }
        annotated.setPersonalInterrestComment(personalInterrestComment);
        annotated.setPersonalInterrestPriority10(personalInterrestPriority10);
        repository.putAnnotation(key, annotated);
    }

    public void removeAnnotation(String key) {
        repository.removeAnnotation(key);
    }

}
