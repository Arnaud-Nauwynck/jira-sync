import { Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JiraIssueDTO } from '../rest/model/jiraIssueDTO';
import { IssueHistoryItemDTO } from '../rest/model/issueHistoryItemDTO';

interface TimelineEntry {
  time: string;
  type: 'created' | 'change' | 'comment' | 'analysis' | 'development';
  title: string;
  author?: string;
  description?: string;
}

@Component({
  imports: [NgClass, FormsModule],
  selector: 'app-timeline-issue-view',
  templateUrl: './timeline-issue-view.html',
})
export class TimelineIssueView {
  @Input() issue?: JiraIssueDTO;

  showDetails = true;

  get entries(): TimelineEntry[] {
    const data = this.issue;
    if (!data) {
      return [];
    }
    const entries: TimelineEntry[] = [];

    if (data.fields?.created) {
      entries.push({
        time: data.fields.created,
        type: 'created',
        title: 'Issue created',
        author: data.fields.creator ?? data.fields.reporter,
      });
    }

    for (const history of data.histories ?? []) {
      if (!history.created) {
        continue;
      }
      for (const item of history.items ?? []) {
        entries.push({
          time: history.created,
          type: 'change',
          title: this.formatFieldChange(item),
          author: history.author,
        });
      }
    }

    for (const comment of data.fields?.comments ?? []) {
      if (!comment.created) {
        continue;
      }
      entries.push({
        time: comment.created,
        type: 'comment',
        title: 'Comment added',
        author: comment.author,
        description: comment.body,
      });
    }

    const annotated = data.annotated;
    if (annotated?.analysisSummaryLastUpdateTime) {
      entries.push({
        time: annotated.analysisSummaryLastUpdateTime,
        type: 'analysis',
        title: 'Analysis updated',
        description: annotated.analysisSummary,
      });
    }
    if (annotated?.developmentWorkLastUpdateTime) {
      entries.push({
        time: annotated.developmentWorkLastUpdateTime,
        type: 'development',
        title: 'Development work updated',
        description: annotated.developmentWorkDescribed,
      });
    }

    return entries.sort((a, b) => new Date(b.time).getTime() - new Date(a.time).getTime());
  }

  summaryLine(entry: TimelineEntry): string {
    const parts = [entry.time];
    if (entry.author) {
      parts.push(entry.author);
    }
    parts.push(entry.title);
    return parts.join(' — ');
  }

  private formatFieldChange(item: IssueHistoryItemDTO): string {
    const field = item.field ?? 'field';
    if (item.fromString && item.toString) {
      return `${field}: ${item.fromString} → ${item.toString}`;
    }
    if (item.toString) {
      return `${field} set to ${item.toString}`;
    }
    if (item.fromString) {
      return `${field} cleared (was ${item.fromString})`;
    }
    return `${field} changed`;
  }
}
