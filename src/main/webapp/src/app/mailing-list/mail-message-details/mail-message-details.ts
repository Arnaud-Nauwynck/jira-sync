import { Component, Input, OnChanges, OnInit, SimpleChanges, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { switchMap } from 'rxjs';
import { MailMessageDTO } from '../../rest';
import { MailMessagesDataService } from '../mail-message-search-page/mail-messages-data.service';
import { MailMessageView } from '../mail-message-view/mail-message-view';

@Component({
  imports: [MailMessageView],
  selector: 'app-mail-message-details',
  templateUrl: './mail-message-details.html',
})
export class MailMessageDetails implements OnInit, OnChanges {

  /** Message id to load when embedded directly (e.g. in a master-detail panel); takes precedence over the route param. */
  @Input() messageId?: string;

  readonly message = signal<MailMessageDTO | undefined>(undefined);
  readonly notFound = signal(false);

  constructor(
    private route: ActivatedRoute,
    private messagesDataService: MailMessagesDataService,
  ) {}

  ngOnInit() {
    if (this.messageId) {
      this.loadMessage(this.messageId);
    } else {
      this.route.paramMap
        .pipe(switchMap((params) => this.messagesDataService.findByMessageId(params.get('messageId')!)))
        .subscribe({
          next: (message) => {
            this.message.set(message);
            this.notFound.set(false);
          },
          error: (err) => {
            console.error('failed to load mail message', err);
            this.message.set(undefined);
            this.notFound.set(true);
          },
        });
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['messageId'] && !changes['messageId'].firstChange && this.messageId) {
      this.loadMessage(this.messageId);
    }
  }

  private loadMessage(messageId: string) {
    this.messagesDataService.findByMessageId(messageId).subscribe({
      next: (message) => {
        this.message.set(message);
        this.notFound.set(false);
      },
      error: (err) => {
        console.error('failed to load mail message', err);
        this.message.set(undefined);
        this.notFound.set(true);
      },
    });
  }
}
