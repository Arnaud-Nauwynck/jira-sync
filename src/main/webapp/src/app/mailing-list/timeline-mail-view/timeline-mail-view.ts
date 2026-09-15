import { Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MailMessageDTO } from '../../rest';

interface TimelineEntry {
  time: string;
  type: 'sent' | 'analysis' | 'development';
  title: string;
  author?: string;
  description?: string;
}

@Component({
  imports: [NgClass, FormsModule],
  selector: 'app-timeline-mail-view',
  templateUrl: './timeline-mail-view.html',
})
export class TimelineMailView {
  @Input() message?: MailMessageDTO;

  showDetails = true;

  get entries(): TimelineEntry[] {
    const msg = this.message;
    if (!msg) {
      return [];
    }
    const entries: TimelineEntry[] = [];

    if (msg.date) {
      entries.push({
        time: msg.date,
        type: 'sent',
        title: msg.inReplyTo ? 'Reply sent' : 'Thread started',
        author: msg.from,
        description: msg.subject,
      });
    }

    const annotated = msg.annotated;
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
}
