import { Routes } from '@angular/router';
import {IssuesList} from './issues-list/issues-list';
import {IssueDetails} from './issue-details/issue-details';
import {JiraSync} from './jira-sync/jira-sync';
import {About} from './about/about';
import {UserIssueStatList} from './user-issue-stat-list/user-issue-stat-list';

export const routes: Routes = [
  {path:'issues', component: IssuesList},
  {path:'issue/:key', component: IssueDetails},
  {path:'user-issue-stats', component: UserIssueStatList},
  {path:'jira-sync', component: JiraSync},
  {path:'about', component: About},

];
