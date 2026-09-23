import { Routes } from '@angular/router';
import {IssuesSearchPage} from './jira/issues-search-page/issues-search-page';
import {IssueDetailsPage} from './jira/issue-details/issue-details';
import {GithubPrSearchPage} from './github/github-pr-search-page/github-pr-search-page';
import {GithubPrDetailsPage} from './github/github-pr-details-page/github-pr-details';
import {MailMessageSearchPage} from './mailing-list/mail-message-search-page/mail-message-search-page';
import {MailMessageDetailsPage} from './mailing-list/mail-message-details-page/mail-message-details-page.component';
import {SourcesSyncPage} from './sources-sync-page/sources-sync.component';
import {AboutPage} from './about-page/about-page.component';
import {UserIssueStatListPage} from './jira/user-issue-stat-list-page/user-issue-stat-list-page.component';
import {UserPrStatListPage} from './github/user-pr-stat-list-page/user-pr-stat-list-page.component';
import {UserMailMessageStatListPage} from './mailing-list/user-mail-message-stat-list-page/user-mail-message-stat-list-page.component';
import {GithubRateLimitPage} from './github/github-rate-limit-page/github-rate-limit-page.component';
import {EventLogPage} from './event-log/event-log-page/event-log-page';
import {UserActivityChartPage} from './user-activity-chart-page/user-activity-chart-page.component';
import {PrDistributionChartPage} from './github/pr-distribution-chart-page/pr-distribution-chart-page.component';
import {ClaudeCodeRunningBatchesPage} from './claude/claude-code-running-batches-page/claude-code-running-batches-page';
import {ClaudeCodeBatchesPage} from './claude/claude-code-batches-page/claude-code-batches-page';

export const routes: Routes = [
  // Jira
  {path:'issues', component: IssuesSearchPage},
  {path:'issue/:key', component: IssueDetailsPage},
  {path:'user-issue-stats', component: UserIssueStatListPage},

  // Github Pull-Request
  {path:'github-pull-requests', component: GithubPrSearchPage},
  {path:'github-pull-request/:number', component: GithubPrDetailsPage},
  {path:'user-pr-stats', component: UserPrStatListPage},
  {path:'pr-distribution-chart', component: PrDistributionChartPage},
  {path:'github-rate-limit', component: GithubRateLimitPage},

  // Mailing List
  {path:'mailing-list', component: MailMessageSearchPage},
  {path:'mailing-list-message/:messageId', component: MailMessageDetailsPage},
  {path:'user-mail-message-stats', component: UserMailMessageStatListPage},

  // Statistics
  {path:'user-activity-chart', component: UserActivityChartPage},

  // Misc
  {path:'sources-sync', component: SourcesSyncPage},
  {path:'event-log', component: EventLogPage},
  {path:'claude-code-running-batches', component: ClaudeCodeRunningBatchesPage},
  {path:'claude-code-batches', component: ClaudeCodeBatchesPage},
  {path:'about', component: AboutPage},

];
