import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { MailMessageCriteriaDTO, MailMessagePartitionStatsDTO, MailMessageDTO, MailMessagesService, NearbyMailMessagesDTO } from '../../rest';

const DEFAULT_LIMIT = 1000;

// Strips the "{yyyy-MM-dd}-" prefix that the search page's "Open As Page" link prepends to the
// messageId (for a more readable / sortable URL, and so the backend can infer the partition month
// from it); falls back to the raw value for a plain messageId.
function extractMessageId(dateMessageIdOrMessageId: string): string {
  const match = /^\d{4}-\d{2}-\d{2}-(.+)$/.exec(dateMessageIdOrMessageId);
  return match ? match[1] : dateMessageIdOrMessageId;
}

@Injectable({ providedIn: 'root' })
export class MailMessagesDataService {

  // Row Data: the last fetched messages, shared with anyone injecting this service.
  readonly messages = signal<MailMessageDTO[]>([]);

  // The criteria (and limit) that produced the current `messages` result list.
  readonly lastCriteria = signal<MailMessageCriteriaDTO | undefined>(undefined);
  readonly lastLimit = signal<number>(DEFAULT_LIMIT);

  // Count of locally-synced messages per "archived" (month) partition and per sender, loaded once and
  // used to show how many messages are available versus how many currently match the search criteria.
  readonly partitionStats = signal<MailMessagePartitionStatsDTO | undefined>(undefined);

  constructor(private mailMessagesService: MailMessagesService) {}

  /**
   * Finds a message by messageId (optionally "{yyyy-MM-dd}-" prefixed, see {@link extractMessageId}),
   * from the currently cached messages if present, otherwise from the server. The date-prefixed form
   * is passed through as-is to the server so it can load only the relevant partition.
   */
  findByMessageId(dateMessageIdOrMessageId: string): Observable<MailMessageDTO> {
    const messageId = extractMessageId(dateMessageIdOrMessageId);
    const cached = this.messages().find((msg) => msg.messageId === messageId);
    if (cached) {
      return of(cached);
    }
    return this.mailMessagesService.findMessageByMessageId(dateMessageIdOrMessageId);
  }

  /** Finds the Message-IDs of the messages nearest to the given one (by Date, overall and from the same sender). */
  findNearbyMessages(messageId: string): Observable<NearbyMailMessagesDTO> {
    return this.mailMessagesService.findNearbyMessages(extractMessageId(messageId));
  }

  /** Re-fetches a message by messageId from the server, bypassing the cache, and updates it in the cache if present. */
  refreshByMessageId(messageId: string): Observable<MailMessageDTO> {
    const result = this.mailMessagesService.findMessageByMessageId(messageId);
    result.subscribe((message) => {
      const messages = this.messages();
      const index = messages.findIndex((msg) => msg.messageId === messageId);
      if (index >= 0) {
        this.messages.set([...messages.slice(0, index), message, ...messages.slice(index + 1)]);
      }
    });
    return result;
  }

  query(criteria: MailMessageCriteriaDTO, limit = DEFAULT_LIMIT) {
    this.mailMessagesService.queryMessages({ criteria, limit })
      .subscribe({
        next: (messages) => {
          this.messages.set(messages);
          this.lastCriteria.set(criteria);
          this.lastLimit.set(limit);
        },
        error: (err) => {
          console.error('failed to load mailing-list messages', err)
        },
      });
  }

  loadPartitionStats() {
    this.mailMessagesService.queryPartitionStats()
      .subscribe({
        next: (stats) => {
          this.partitionStats.set(stats);
        },
        error: (err) => {
          console.error('failed to load mailing-list partition stats', err)
        },
      });
  }
}
