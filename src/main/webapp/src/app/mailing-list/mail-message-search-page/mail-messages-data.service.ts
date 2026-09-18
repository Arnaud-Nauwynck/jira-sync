import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { MailMessageCriteriaDTO, MailMessagePartitionStatsDTO, MailMessageDTO, MailMessagesService } from '../../rest';

const DEFAULT_LIMIT = 1000;

@Injectable({ providedIn: 'root' })
export class MailMessagesDataService {

  // Row Data: the last fetched messages, shared with anyone injecting this service.
  readonly messages = signal<MailMessageDTO[]>([]);

  // Count of locally-synced messages per "archived" (month) partition and per sender, loaded once and
  // used to show how many messages are available versus how many currently match the search criteria.
  readonly partitionStats = signal<MailMessagePartitionStatsDTO | undefined>(undefined);

  constructor(private mailMessagesService: MailMessagesService) {}

  /** Finds a message by messageId, from the currently cached messages if present, otherwise from the server. */
  findByMessageId(messageId: string): Observable<MailMessageDTO> {
    const cached = this.messages().find((msg) => msg.messageId === messageId);
    if (cached) {
      return of(cached);
    }
    return this.mailMessagesService.findMessageByMessageId(messageId);
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
