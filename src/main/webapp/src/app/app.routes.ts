import { Routes } from '@angular/router';
import {IssuesList} from './issues-list/issues-list';
import {JiraSync} from './jira-sync/jira-sync';
import {About} from './about/about';

export const routes: Routes = [
  {path:'issues', component: IssuesList},
  {path:'jira-sync', component: JiraSync},
  {path:'about', component: About},

];
