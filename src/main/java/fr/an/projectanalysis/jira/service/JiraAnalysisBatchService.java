package fr.an.projectanalysis.jira.service;

import fr.an.projectanalysis.claude.service.ClaudeCodePromptRunningBatch;
import fr.an.projectanalysis.jira.repository.JiraIssueRepository;
import fr.an.projectanalysis.jira.rest.dtos.JiraIssueDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Launches the claude-code "/jira-analysis" prompt for several Jira issues, strictly one after the other. */
@Service
@Slf4j
public class JiraAnalysisBatchService {

    private final JiraAnalysisLauncher jiraAnalysisLauncher;

    private final JiraIssueRepository jiraIssueRepository;

    private final int parallelism;

    public JiraAnalysisBatchService(JiraAnalysisLauncher jiraAnalysisLauncher, JiraIssueRepository jiraIssueRepository,
            @Value("${jira.analysis.parallelism:15}") int parallelism) {
        this.parallelism = parallelism;
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
                ClaudeCodePromptRunningBatch runningBatch = jiraAnalysisLauncher.launchClaudeJiraAnalysisOrSkip(jiraKey);
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

    /** Launches the analysis of the keys in parallel, on a thread pool limited to 'parallelism' concurrent analyses,
     * and waits for all of them to finish.
     * A key already having an analysisSummary is skipped. A key whose launch fails is logged and skipped.
     * @return the successfully launched (and finished) batches, in order */
    public List<ClaudeCodePromptRunningBatch> analyzeAllInParallel(List<String> jiraKeys) {
        List<JiraIssueDTO> toProcess = loadIssuesToProcess(jiraKeys);
        int totalToProcess = toProcess.size();
        AtomicInteger doneCount = new AtomicInteger();
        List<Future<ClaudeCodePromptRunningBatch>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, parallelism));
        try {
            int count = 0;
            for (JiraIssueDTO issue : toProcess) {
                int index = ++count;
                futures.add(executor.submit(() -> analyzeOne(issue, index, totalToProcess, doneCount)));
            }
            return collectResults(futures);
        } finally {
            executor.shutdownNow();
        }
    }

    private ClaudeCodePromptRunningBatch analyzeOne(JiraIssueDTO issue, int index, int total, AtomicInteger doneCount) throws InterruptedException {
        String jiraKey = issue.getKey();
        log.info("Analyzing [{}/{}] jira {}: {}", index, total, jiraKey, title(issue));
        try {
            ClaudeCodePromptRunningBatch runningBatch = jiraAnalysisLauncher.launchClaudeJiraAnalysisOrSkip(jiraKey);
            runningBatch.getCompletion().get();
            return runningBatch;
        } catch (IOException | ExecutionException ex) {
            log.error("Failed to launch jira analysis for {}, skip", jiraKey, ex);
            return null;
        } finally {
            int done = doneCount.incrementAndGet();
            log.info("Done [{}/{}] analysis, jira {}", done, total, jiraKey);
        }
    }

    private List<ClaudeCodePromptRunningBatch> collectResults(List<Future<ClaudeCodePromptRunningBatch>> futures) {
        List<ClaudeCodePromptRunningBatch> res = new ArrayList<>();
        for (Future<ClaudeCodePromptRunningBatch> future : futures) {
            try {
                ClaudeCodePromptRunningBatch batch = future.get();
                if (batch != null) {
                    res.add(batch);
                }
            } catch (ExecutionException ex) {
                log.error("Unexpected failure in jira analysis task, skip", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for jira analyses, stop waiting");
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
