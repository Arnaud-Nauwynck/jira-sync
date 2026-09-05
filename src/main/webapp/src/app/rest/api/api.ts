export * from './jiraIssues.service';
import { JiraIssuesService } from './jiraIssues.service';
export * from './jiraSync.service';
import { JiraSyncService } from './jiraSync.service';
export const APIS = [JiraIssuesService, JiraSyncService];
