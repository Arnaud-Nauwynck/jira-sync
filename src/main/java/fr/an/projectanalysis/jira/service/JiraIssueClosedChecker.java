package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Tells whether a Jira issue is already finished (closed, resolved, rejected, won't fix...). */
@Component
public class JiraIssueClosedChecker {

    /** lower-case status names, with non-letter characters removed */
    private static final Set<String> CLOSED_STATUSES = Set.of(
            "closed", "resolved", "done", "complete", "completed", "rejected", "wontfix", "wontdo",
            "invalid", "duplicate", "cannotreproduce", "notaproblem", "abandoned", "obsolete");

    public boolean isClosed(JiraIssueDTO issue) {
        return issue != null && issue.fields != null && isClosedStatus(issue.fields.status);
    }

    private static boolean isClosedStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toLowerCase().replaceAll("[^a-z]", "");
        return CLOSED_STATUSES.contains(normalized);
    }

}
