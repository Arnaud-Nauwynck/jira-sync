package fr.an.projectanalysis.service;

import fr.an.projectanalysis.github.service.GitHubPullRequestService;
import fr.an.projectanalysis.jira.service.JiraIssueService;
import fr.an.projectanalysis.mailinglist.service.MailMessageService;
import fr.an.projectanalysis.rest.dtos.UserActivityStatsDTO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Combines each source's per-user activity contributions (Jira issue, GitHub pull-request and
 * mailing-list message create/update/comment/close events) into a single per-user, per-month
 * activity view.
 */
@Component
public class UserActivityStatsService {

    private final JiraIssueService jiraIssueService;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final MailMessageService mailMessageService;

    public UserActivityStatsService(JiraIssueService jiraIssueService,
            GitHubPullRequestService gitHubPullRequestService,
            MailMessageService mailMessageService) {
        this.jiraIssueService = jiraIssueService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.mailMessageService = mailMessageService;
    }

    /** Combined per-user, per-month activity stats, for events between fromYear and toYear (inclusive). */
    public Collection<UserActivityStatsDTO> queryUserActivityStats(int fromYear, int toYear) {
        Map<String, UserActivityStatsDTO> acc = new LinkedHashMap<>();
        String fromMonth = fromYear + "-01";
        String toMonth = toYear + "-12";
        jiraIssueService.contributeUserActivityStats(acc, fromYear, toYear);
        gitHubPullRequestService.contributeUserActivityStats(acc, fromYear, toYear);
        mailMessageService.contributeUserActivityStats(acc, fromMonth, toMonth);
        return acc.values();
    }

}
