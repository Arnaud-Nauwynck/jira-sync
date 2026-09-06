import { Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';
import { AnnotatedJiraIssueDTO } from '../rest/model/annotatedJiraIssueDTO';

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
  selector: 'app-issue-view',
  templateUrl: './issue-view.html',
})
export class IssueView {
  @Input() issue?: AnnotatedJiraIssueDTO;

  statusLozengeClass(status: string | undefined): string {
    return STATUS_LOZENGE_CLASS[(status ?? '').toLowerCase()] ?? 'lozenge-default';
  }
}
