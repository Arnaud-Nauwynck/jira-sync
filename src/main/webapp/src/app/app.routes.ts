import { Routes } from '@angular/router';
import {IssuesList} from './issues-list/issues-list';
import {IssueDetails} from './issue-details/issue-details';
import {GithubPRList} from './github-pr-list/github-pr-list';
import {MailMessageList} from './mail-message-list/mail-message-list';
import {JiraSync} from './jira-sync/jira-sync';
import {About} from './about/about';
import {UserIssueStatList} from './user-issue-stat-list/user-issue-stat-list';
import {UserPrStatList} from './user-pr-stat-list/user-pr-stat-list';
import {UserMailMessageStatList} from './user-mail-message-stat-list/user-mail-message-stat-list';

export const routes: Routes = [
  {path:'issues', component: IssuesList},
  {path:'issue/:key', component: IssueDetails},
  {path:'github-pull-requests', component: GithubPRList},
  {path:'mailing-list', component: MailMessageList},
  {path:'user-issue-stats', component: UserIssueStatList},
  {path:'user-pr-stats', component: UserPrStatList},
  {path:'user-mail-message-stats', component: UserMailMessageStatList},
  {path:'jira-sync', component: JiraSync},
  {path:'about', component: About},

];
