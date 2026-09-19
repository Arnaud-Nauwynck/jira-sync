import { Component, Input, OnChanges, OnInit, SimpleChanges, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MailMessageDTO, NearbyMailMessagesDTO } from '../../rest';
import { MailMessagesDataService } from '../mail-message-search-page/mail-messages-data.service';
import { MailMessageView } from '../mail-message-view/mail-message-view';

@Component({
  imports: [MailMessageView],
  selector: 'app-mail-message-details',
  templateUrl: './mail-message-details-page.component.html',
})
export class MailMessageDetailsPage implements OnInit, OnChanges {

  /** Message id to load when embedded directly (e.g. in a master-detail panel); takes precedence over the route param. */
  @Input() messageId?: string;

  readonly message = signal<MailMessageDTO | undefined>(undefined);
  readonly nearby = signal<NearbyMailMessagesDTO | undefined>(undefined);
  readonly notFound = signal(false);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private messagesDataService: MailMessagesDataService,
  ) {}

  ngOnInit() {
    if (this.messageId) {
      this.loadMessage(this.messageId);
    } else {
      this.route.paramMap.subscribe((params) => {
        const id = params.get('messageId');
        if (id) {
          this.loadMessage(id);
        }
      });
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['messageId'] && !changes['messageId'].firstChange && this.messageId) {
      this.loadMessage(this.messageId);
    }
  }

  /** Navigates the "prev"/"next" toolbar buttons to another message: reloads in place when embedded
   * (driven by the `messageId` @Input, not the route), otherwise navigates the route. */
  goToMessage(messageId: string | undefined | null) {
    if (!messageId) {
      return;
    }
    if (this.messageId) {
      this.loadMessage(messageId);
    } else {
      this.router.navigate(['/mailing-list-message', messageId]);
    }
  }

  private loadMessage(messageId: string) {
    this.nearby.set(undefined);
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
    this.messagesDataService.findNearbyMessages(messageId).subscribe({
      next: (nearby) => this.nearby.set(nearby),
      error: (err) => console.error('failed to load nearby mail messages', err),
    });
  }
}
