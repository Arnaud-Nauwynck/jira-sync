import { Routes } from '@angular/router';
import {IssuesSearchPage} from './jira/issues-search-page/issues-search-page';
import {IssueDetailsPage} from './jira/issue-details/issue-details';
import {GithubPrSearchPage} from './github/github-pr-search-page/github-pr-search-page';
import {GithubPrDetailsPage} from './github/github-pr-details/github-pr-details';
import {MailMessageSearchPage} from './mailing-list/mail-message-search-page/mail-message-search-page';
import {MailMessageDetails} from './mailing-list/mail-message-details/mail-message-details';
import {SourcesSync} from './sources-sync/sources-sync.component';
import {About} from './about/about';
import {UserIssueStatList} from './jira/user-issue-stat-list/user-issue-stat-list';
import {UserPrStatList} from './github/user-pr-stat-list/user-pr-stat-list';
import {UserMailMessageStatList} from './mailing-list/user-mail-message-stat-list/user-mail-message-stat-list';
import {GithubRateLimit} from './github/github-rate-limit/github-rate-limit';

export const routes: Routes = [
  {path:'issues', component: IssuesSearchPage},
  {path:'issue/:key', component: IssueDetailsPage},
  {path:'github-pull-requests', component: GithubPrSearchPage},
  {path:'github-pull-request/:number', component: GithubPrDetailsPage},
  {path:'mailing-list', component: MailMessageSearchPage},
  {path:'mailing-list-message/:messageId', component: MailMessageDetails},
  {path:'user-issue-stats', component: UserIssueStatList},
  {path:'user-pr-stats', component: UserPrStatList},
  {path:'user-mail-message-stats', component: UserMailMessageStatList},
  {path:'sources-sync', component: SourcesSync},
  {path:'github-rate-limit', component: GithubRateLimit},
  {path:'about', component: About},

];
