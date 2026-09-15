import { Injectable, signal } from '@angular/core';
import { MailMessagesService } from '../../rest';
import { MailMessageDTO } from '../../rest';

@Injectable({ providedIn: 'root' })
export class MailMessagesDataService {

  // Row Data: the last fetched messages, shared with anyone injecting this service.
  readonly messages = signal<MailMessageDTO[]>([]);

  constructor(private mailMessagesService: MailMessagesService) {}

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
