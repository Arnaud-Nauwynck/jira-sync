import { Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';
import { JiraIssueDTO } from '../rest/model/jiraIssueDTO';

const STATUS_LOZENGE_CLASS: Record<string, string> = {
  'open': 'lozenge-default',
  'reopened': 'lozenge-default',
  'in progress': 'lozenge-inprogress',
  'resolved': 'lozenge-success',
  'closed': 'lozenge-success',
  'done': 'lozenge-success',
};

@Component({
  imports: [NgClass],
  selector: 'app-jira-issue-view',
  templateUrl: './jira-issue-view.html',
})
export class JiraIssueView {
  @Input() issue?: JiraIssueDTO;

  statusLozengeClass(status: string | undefined): string {
    return STATUS_LOZENGE_CLASS[(status ?? '').toLowerCase()] ?? 'lozenge-default';
  }
}
