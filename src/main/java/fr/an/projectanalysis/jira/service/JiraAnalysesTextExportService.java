package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.repository.JiraIssueRepository;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Builds a single text document containing, for each Jira issue having an analysis, its description followed by the analysis text. */
@Service
public class JiraAnalysesTextExportService {

    private final JiraIssueRepository repository;
    private final JiraKeyToPullRequestsIndexer pullRequestsIndexer;

    public JiraAnalysesTextExportService(JiraIssueRepository repository,
                                         JiraKeyToPullRequestsIndexer pullRequestsIndexer) {
        this.repository = repository;
        this.pullRequestsIndexer = pullRequestsIndexer;
    }

    public String exportAllAnalyses(int fromYear, int toYear) {
        Map<String, List<Integer>> prsByKey = pullRequestsIndexer.buildIndex();
        StringBuilder sb = new StringBuilder();
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            if (issue.annotated == null || issue.annotated.analysisSummary == null
                    || issue.annotated.analysisSummary.isBlank()) {
                return;
            }
            appendIssue(sb, issue, prsByKey.get(issue.key));
        });
        return sb.toString();
    }

    private static void appendIssue(StringBuilder sb, JiraIssueDTO issue, List<Integer> prNumbers) {
        JiraIssueDTO.IssueFieldsDTO f = issue.fields;
        sb.append("=== ").append(issue.key).append(": ").append(f != null && f.summary != null ? f.summary : "").append('\n');
        if (f != null) {
            appendField(sb, "issueType", f.issuetype);
            appendField(sb, "status", f.status);
            appendField(sb, "resolution", f.resolution);
            appendField(sb, "priority", f.priority);
            appendField(sb, "created", f.created);
            appendField(sb, "updated", f.updated);
            appendField(sb, "resolutionDate", f.resolutiondate);
            if (f.components != null && !f.components.isEmpty()) {
                appendField(sb, "components", String.join(", ", f.components));
            }
        }
        boolean hasPr = prNumbers != null && !prNumbers.isEmpty();
        appendField(sb, "pullRequestAvailable", hasPr ? "yes (#" + joinNumbers(prNumbers) + ")" : "no");
        if (f != null && f.description != null && !f.description.isBlank()) {
            sb.append("--- Description:\n").append(f.description.strip()).append('\n');
        }
        sb.append("--- Analysis:\n").append(issue.annotated.analysisSummary.strip()).append("\n\n");
    }

    private static void appendField(StringBuilder sb, String name, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(name).append(": ").append(value).append('\n');
        }
    }

    private static String joinNumbers(List<Integer> numbers) {
        StringBuilder sb = new StringBuilder();
        for (Integer n : numbers) {
            if (!sb.isEmpty()) {
                sb.append(", #");
            }
            sb.append(n);
        }
        return sb.toString();
    }

}
