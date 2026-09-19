package fr.an.projectanalysis.service;

import fr.an.projectanalysis.github.service.GitHubPullRequestService;
import fr.an.projectanalysis.jira.service.JiraIssueService;
import fr.an.projectanalysis.mailinglist.service.MailMessageService;
import fr.an.projectanalysis.rest.dtos.UserActivityStatsResultDTO;
import org.springframework.stereotype.Component;

/**
 * Gathers each source's per-user activity contributions (Jira issue, GitHub pull-request and
 * mailing-list message create/update/comment/close events). The 3 sources are kept as separate
 * per-user, per-month maps rather than merged into one: each uses its own user identity (Jira
 * reporter, GitHub login, mail "From" address), which are not cross-mapped to one another.
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

    /** Per-user, per-month activity stats for each of the 3 sources, for events between fromYear and toYear (inclusive). */
    public UserActivityStatsResultDTO queryUserActivityStats(int fromYear, int toYear) {
        UserActivityStatsResultDTO res = new UserActivityStatsResultDTO();
        String fromMonth = fromYear + "-01";
        String toMonth = toYear + "-12";
        jiraIssueService.contributeUserActivityStats(res.jira, fromYear, toYear);
        gitHubPullRequestService.contributeUserActivityStats(res.github, fromYear, toYear);
        mailMessageService.contributeUserActivityStats(res.mail, fromMonth, toMonth);
        return res;
    }

}
