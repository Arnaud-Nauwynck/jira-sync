package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.claude.service.ClaudeCodePromptRunningBatch;
import fr.an.projectanalysis.jira.repository.JiraIssueRepository;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Launches the claude-code "/jira-analysis" prompt for several Jira issues, strictly one after the other. */
@Service
@Slf4j
public class JiraAnalysisBatchService {

    private final JiraAnalysisLauncher jiraAnalysisLauncher;

    private final JiraIssueRepository jiraIssueRepository;

    public JiraAnalysisBatchService(JiraAnalysisLauncher jiraAnalysisLauncher, JiraIssueRepository jiraIssueRepository) {
        this.jiraIssueRepository = jiraIssueRepository;
        this.jiraAnalysisLauncher = jiraAnalysisLauncher;
    }

    /** Launches the analysis of each key, waiting for each one to finish before starting the next.
     * A key already having an analysisSummary is skipped. A key whose launch fails is logged and skipped; interruption stops the remaining ones.
     * @return the successfully launched (and finished) batches, in order */
    public List<ClaudeCodePromptRunningBatch> analyzeAll(List<String> jiraKeys) {
        List<ClaudeCodePromptRunningBatch> res = new ArrayList<>();
        List<JiraIssueDTO> toProcess = loadIssuesToProcess(jiraKeys);
        int totalToProcess = toProcess.size();
        int count = 0;
        for (JiraIssueDTO issue : toProcess) {
            count++;
            String jiraKey = issue.getKey();
            log.info("Analyzing [{}/{}] jira {}: {}", count, totalToProcess, jiraKey, title(issue));
            try {
                ClaudeCodePromptRunningBatch runningBatch = jiraAnalysisLauncher.launchClaudeJiraAnalysis(jiraKey);
                res.add(runningBatch);
                runningBatch.getCompletion().get();
            } catch (IOException | ExecutionException ex) {
                log.error("Failed to launch jira analysis for {}, skip", jiraKey, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while analyzing {}, stop remaining", jiraKey);
                break;
            }
        }
        return res;
    }

    private List<JiraIssueDTO> loadIssuesToProcess(List<String> jiraKeys) {
        List<JiraIssueDTO> res = new ArrayList<>();
        for (String jiraKey : jiraKeys) {
            JiraIssueDTO issue = jiraIssueRepository.findByKey(jiraKey);
            if (issue == null) {
                log.warn("Skip jira analysis for {}: issue not found", jiraKey);
            } else if (hasAnalysis(issue)) {
                log.info("Skip jira analysis for {}: analysisSummary already available", jiraKey);
            } else {
                res.add(issue);
            }
        }
        return res;
    }

    private static String title(JiraIssueDTO issue) {
        return issue != null && issue.fields != null ? issue.fields.summary : null;
    }

    private boolean hasAnalysis(JiraIssueDTO issue) {
        return issue != null && issue.annotated != null
                && issue.annotated.analysisSummary != null && !issue.annotated.analysisSummary.isBlank();
    }

}
