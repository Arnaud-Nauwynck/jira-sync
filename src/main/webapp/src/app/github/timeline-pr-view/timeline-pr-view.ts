import { Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GitHubPullRequestDTO } from '../../rest';

interface TimelineEntry {
  time: string;
  type: 'created' | 'merged' | 'closed' | 'analysis' | 'development' | 'review-comment' | 'comment';
  title: string;
  author?: string;
  description?: string;
}

@Component({
  imports: [NgClass, FormsModule],
  selector: 'app-timeline-pr-view',
  templateUrl: './timeline-pr-view.html',
})
export class TimelinePrView {
  @Input() pullRequest?: GitHubPullRequestDTO;

  showDetails = true;

  get entries(): TimelineEntry[] {
    const pr = this.pullRequest;
    if (!pr) {
      return [];
    }
    const entries: TimelineEntry[] = [];

    if (pr.createdAt) {
      entries.push({
        time: pr.createdAt,
        type: 'created',
        title: 'Pull request opened',
        author: pr.authorLogin,
        description: pr.body,
      });
    }

    if (pr.merged && pr.mergedAt) {
      entries.push({
        time: pr.mergedAt,
        type: 'merged',
        title: 'Pull request merged',
        author: pr.mergedByLogin,
      });
    } else if (pr.closedAt) {
      entries.push({
        time: pr.closedAt,
        type: 'closed',
        title: 'Pull request closed',
      });
    }

    const annotated = pr.annotated;
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

    for (const comment of pr.commentsData ?? []) {
      if (!comment.createdAt) {
        continue;
      }
      entries.push({
        time: comment.createdAt,
        type: 'comment',
        title: 'Comment',
        author: comment.authorLogin,
        description: comment.body,
      });
    }

    for (const comment of pr.reviewCommentsData ?? []) {
      if (!comment.createdAt) {
        continue;
      }
      entries.push({
        time: comment.createdAt,
        type: 'review-comment',
        title: comment.path ? `Review comment on ${comment.path}${comment.line ? ':' + comment.line : ''}` : 'Review comment',
        author: comment.authorLogin,
        description: comment.body,
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
}
