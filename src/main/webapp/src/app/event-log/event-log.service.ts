import { Injectable, signal } from '@angular/core';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import {ChangeLogEvent, ChangeLogService, GithubPRChange, JiraIssueChange, MailingListChange} from '../rest';
import { IssuesDataService } from '../jira/service/issues-data.service';
import { GithubPullRequestsDataService } from '../github/service/github-pull-requests-data.service';
import { MailMessagesDataService } from '../mailing-list/mail-message-search-page/mail-messages-data.service';

const DEFAULT_MAX_EVENTS = 500;
const POLL_INTERVAL_MS = 5000;

/**
 * Polls the server's {@link ChangeLogService} for recent Jira issue / GitHub PR / mailing-list
 * changes, keeps an in-memory rolling window of them (most recent first), and, for each incoming
 * event, refreshes the corresponding entity in whichever *DataService already caches it (so open
 * grids/pages pick up the change without a manual reload), or invalidates that cache when the entity
 * is not held yet. Polling can be suspended/resumed, e.g. while the event-log page is not visible.
 */
@Injectable({ providedIn: 'root' })
export class EventLogService {

  // Most recent events first, capped at DEFAULT_MAX_EVENTS.
  readonly events = signal<ChangeLogEvent[]>([]);

  readonly suspended = signal(true);

  private lastSeq = 0;
  private pollSubscription?: Subscription;

  constructor(
    private changeLogService: ChangeLogService,
    private issuesDataService: IssuesDataService,
    private githubPullRequestsDataService: GithubPullRequestsDataService,
    private mailMessagesDataService: MailMessagesDataService,
  ) {}

  resume() {
    if (this.pollSubscription) {
      return;
    }
    this.suspended.set(false);
    this.pollSubscription = timer(0, POLL_INTERVAL_MS)
      .pipe(switchMap(() => this.changeLogService.getEventsSince(this.lastSeq)))
      .subscribe({
        next: (newEvents) => this.onNewEvents(newEvents),
        error: (err) => console.error('failed to poll change-log events', err),
      });
  }

  suspend() {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = undefined;
    this.suspended.set(true);
  }

  clear() {
    this.events.set([]);
  }

  private onNewEvents(newEvents: ChangeLogEvent[]) {
    if (newEvents.length === 0) {
      return;
    }
    for (const event of newEvents) {
      this.lastSeq = Math.max(this.lastSeq, event.seq ?? 0);
      this.refreshCachedEntity(event);
    }
    const mostRecentFirst = [...newEvents].reverse();
    this.events.set([...mostRecentFirst, ...this.events()].slice(0, DEFAULT_MAX_EVENTS));
  }

  /** Re-fetches the entity the event refers to, but only when it's already held by the relevant
   * *DataService cache — no point fetching entities nothing currently displays. An entity held by
   * none of them is a new one: the result sets of the server changed, so the data service is told
   * not to compute its next search as a difference with the result it currently holds. */
  private refreshCachedEntity(event: ChangeLogEvent) {
    switch (event.eventType) {
      case 'jiraIssue': {
        const event2 = <JiraIssueChange>event;
        const key = event2.issueKey;
        if (key) {
          if (this.issuesDataService.isCached(key)) {
            this.issuesDataService.refreshByKey(key).subscribe({ error: (err) => console.error('failed to refresh jira issue', key, err) });
          } else {
            this.issuesDataService.invalidateDeltaBaseline();
          }
        }
        break;
      }
      case 'githubPR': {
        const event2 = <GithubPRChange>event;
        const number = event2.number;
        if (number != null) {
          if (this.githubPullRequestsDataService.isCached(number)) {
            this.githubPullRequestsDataService.refreshByNumber(number).subscribe({ error: (err) => console.error('failed to refresh github PR', number, err) });
          } else {
            this.githubPullRequestsDataService.invalidateDeltaBaseline();
          }
        }
        break;
      }
      case 'mailingList': {
        const event2 = <MailingListChange> event;
        const messageId = event2.messageId;
        if (messageId) {
          if (this.mailMessagesDataService.isCached(messageId)) {
            this.mailMessagesDataService.refreshByMessageId(messageId).subscribe({ error: (err) => console.error('failed to refresh mailing-list message', messageId, err) });
          } else {
            this.mailMessagesDataService.invalidateDeltaBaseline();
          }
        }
        break;
      }
    }
  }
}
