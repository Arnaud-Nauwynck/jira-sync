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
        return queryAnnotatedIssues(fromYear, toYear, usernamePatternText, null, null, null);
    }

    /** Lists the issues created between fromYear and toYear (inclusive), optionally filtered by creator username,
     * issue number range (the numeric suffix of the key), and/or a regex on the full issue key. */
    public List<JiraIssueDTO> queryAnnotatedIssues(int fromYear, int toYear, String usernamePatternText,
            Integer fromNumber, Integer toNumber, String keyPatternText) {
        List<JiraIssueDTO> result = new ArrayList<>();
        Pattern usernamePattern = (usernamePatternText != null && !usernamePatternText.isBlank())? Pattern.compile(usernamePatternText) : null;
        Pattern keyPattern = (keyPatternText != null && !keyPatternText.isBlank())? Pattern.compile(keyPatternText) : null;
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            boolean matches = (usernamePattern == null || usernamePattern.matcher(creatorOf(issue)).matches())
                    && (keyPattern == null || (issue.key != null && keyPattern.matcher(issue.key).matches()))
                    && matchesNumberRange(issue.key, fromNumber, toNumber);
            if (matches) {
                result.add(issue);
            }
        });
        return result;
    }

    /** Whether the numeric suffix of the key (eg "123" in "PROJ-123") falls within [fromNumber, toNumber] (inclusive, either bound optional). */
    private static boolean matchesNumberRange(String key, Integer fromNumber, Integer toNumber) {
        if (fromNumber == null && toNumber == null) {
            return true;
        }
        Integer number = issueNumberOf(key);
        if (number == null) {
            return false;
        }
        if (fromNumber != null && number < fromNumber) {
            return false;
        }
        if (toNumber != null && number > toNumber) {
            return false;
        }
        return true;
    }

    private static Integer issueNumberOf(String key) {
        if (key == null) {
            return null;
        }
        int dashIdx = key.lastIndexOf('-');
        if (dashIdx < 0 || dashIdx == key.length() - 1) {
            return null;
        }
        try {
            return Integer.parseInt(key.substring(dashIdx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
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
