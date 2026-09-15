import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { MailMessagesService } from '../../rest';
import { MailMessageDTO } from '../../rest';

@Injectable({ providedIn: 'root' })
export class MailMessagesDataService {

  // Row Data: the last fetched messages, shared with anyone injecting this service.
  readonly messages = signal<MailMessageDTO[]>([]);

  constructor(private mailMessagesService: MailMessagesService) {}

  /** Finds a message by messageId, from the currently cached messages if present, otherwise from the server. */
  findByMessageId(messageId: string): Observable<MailMessageDTO> {
    const cached = this.messages().find((msg) => msg.messageId === messageId);
    if (cached) {
      return of(cached);
    }
    return this.mailMessagesService.findMessageByMessageId(messageId);
  }

  search(fromMonth?: string, toMonth?: string, fromPattern?: string, subjectPattern?: string, bodyPattern?: string) {
    this.mailMessagesService.queryMessages(fromMonth || undefined, toMonth || undefined, fromPattern || undefined,
        subjectPattern || undefined, bodyPattern || undefined,
        'body', false, { httpHeaderAccept: 'application/json' as any })
      .subscribe({
        next: (messages) => {
          this.messages.set(messages);
        },
        error: (err) => {
          console.error('failed to load mailing-list messages', err)
        },
      });
  }
}
