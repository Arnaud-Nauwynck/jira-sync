import { Routes } from '@angular/router';
import {IssuesList} from './jira/issues-list/issues-list';
import {IssueDetails} from './jira/issue-details/issue-details';
import {GithubPRList} from './github/github-pr-list/github-pr-list';
import {MailMessageList} from './mailing-list/mail-message-list/mail-message-list';
import {SourcesSync} from './sources-sync/sources-sync.component';
import {About} from './about/about';
import {UserIssueStatList} from './jira/user-issue-stat-list/user-issue-stat-list';
import {UserPrStatList} from './github/user-pr-stat-list/user-pr-stat-list';
import {UserMailMessageStatList} from './mailing-list/user-mail-message-stat-list/user-mail-message-stat-list';

export const routes: Routes = [
  {path:'issues', component: IssuesList},
  {path:'issue/:key', component: IssueDetails},
  {path:'github-pull-requests', component: GithubPRList},
  {path:'mailing-list', component: MailMessageList},
  {path:'user-issue-stats', component: UserIssueStatList},
  {path:'user-pr-stats', component: UserPrStatList},
  {path:'user-mail-message-stats', component: UserMailMessageStatList},
  {path:'sources-sync', component: SourcesSync},
  {path:'about', component: About},

];
