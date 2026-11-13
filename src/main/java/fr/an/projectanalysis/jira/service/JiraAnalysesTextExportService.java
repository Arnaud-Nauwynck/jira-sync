package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.jira.repository.JiraIssueRepository;
import org.springframework.stereotype.Service;

/** Builds a single text document containing, for each Jira issue having an analysis, its summary followed by the analysis text. */
@Service
public class JiraAnalysesTextExportService {

    private final JiraIssueRepository repository;

    public JiraAnalysesTextExportService(JiraIssueRepository repository) {
        this.repository = repository;
    }

    public String exportAllAnalyses(int fromYear, int toYear) {
        StringBuilder sb = new StringBuilder();
        repository.scanIssues(fromYear, toYear, (year, issue) -> {
            if (issue.annotated == null || issue.annotated.analysisSummary == null
                    || issue.annotated.analysisSummary.isBlank()) {
                return;
            }
            String summary = (issue.fields != null) ? issue.fields.summary : null;
            sb.append("=== ").append(issue.key).append(": ").append(summary != null ? summary : "").append('\n');
            sb.append(issue.annotated.analysisSummary.strip()).append("\n\n");
        });
        return sb.toString();
    }

}
