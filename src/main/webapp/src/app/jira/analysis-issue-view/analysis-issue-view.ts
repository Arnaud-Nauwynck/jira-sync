import { Component, Input, signal } from '@angular/core';
import { IssueExtraFieldsDTO, JiraIssuesService } from '../../rest';

@Component({
  selector: 'app-analysis-issue-view',
  templateUrl: './analysis-issue-view.html',
})
export class AnalysisIssueView {
  @Input() jiraKey?: string;
  @Input() annotated?: IssueExtraFieldsDTO;

  launching = signal(false);
  launchError = signal<string | undefined>(undefined);

  constructor(private jiraIssuesService: JiraIssuesService) {}

  launchClaudeAnalysis() {
    const jiraKey = this.jiraKey;
    if (!jiraKey) {
      return;
    }
    this.launching.set(true);
    this.launchError.set(undefined);
    this.jiraIssuesService.launchClaudeJiraAnalysis(jiraKey).subscribe({
      next: () => {
        this.launching.set(false);
      },
      error: (ex) => {
        this.launching.set(false);
        this.launchError.set(String(ex?.message ?? ex));
        console.error('... Failed call launchClaudeJiraAnalysis', ex);
      },
    });
  }
}
