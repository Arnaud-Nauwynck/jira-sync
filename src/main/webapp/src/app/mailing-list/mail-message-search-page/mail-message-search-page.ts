import { Component, OnInit, ViewChild, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MailMessageDTO } from '../../rest';
import { MailMessagesDataService } from './mail-messages-data.service';
import { MailMessageView } from '../mail-message-view/mail-message-view';
import { MailMessageListView } from '../mail-message-list-view/mail-message-list-view';
import { MailMessageCriteriaView } from '../mail-message-criteria-view/mail-message-criteria-view';

@Component({
  imports: [FormsModule, MailMessageView, MailMessageListView, MailMessageCriteriaView],
  selector: 'app-mail-message-list',
  templateUrl: './mail-message-search-page.html',
})
export class MailMessageSearchPage implements OnInit {

  @ViewChild(MailMessageCriteriaView, { static: true }) criteriaView!: MailMessageCriteriaView;
  @ViewChild(MailMessageListView, { static: true }) listView!: MailMessageListView;

  // The message currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedMessage = signal<MailMessageDTO | undefined>(undefined);

  // Cap passed to the server on a normal search.
  limit = 5000;
  // Higher cap used instead of `limit` when the currently selected [fromMonth, toMonth] range's full
  // partition data still fits under it: the whole range is then fetched and cached client-side, so
  // further Main/Analysis/Development Work/Personal Interest criteria tweaks never need a re-fetch.
  cachedLimit = 10000;

  constructor(readonly messagesDataService: MailMessagesDataService, private router: Router) {}

  ngOnInit() {
    this.search();
    this.messagesDataService.loadPartitionStats();
  }

  closeDetail() {
    this.selectedMessage.set(undefined);
  }

  openDetailAsRoute() {
    const messageId = this.selectedMessage()?.messageId;
    if (messageId) {
      this.router.navigate(['/mailing-list-message', messageId]);
    }
  }

  search() {
    this.messagesDataService.query(this.criteriaView.criteria, this.effectiveLimit());
  }

  /** Uses `cachedLimit` instead of `limit` when the [fromMonth, toMonth] range's full partition data
   * (per the partition stats) still fits under it, so the fetch captures the whole range. */
  private effectiveLimit(): number {
    const available = this.availableInRange();
    return available > 0 && available <= this.cachedLimit ? this.cachedLimit : this.limit;
  }

  /** Sum of the "archived" (month) partition counts falling within the current [fromMonth, toMonth] criteria. */
  availableInRange(): number {
    const stats = this.messagesDataService.partitionStats()?.statsPerMonth ?? [];
    const fromMonth = this.criteriaView?.criteria.fromMonth;
    const toMonth = this.criteriaView?.criteria.toMonth;
    return stats
      .filter((s) => (!fromMonth || (s.month ?? '') >= fromMonth) && (!toMonth || (s.month ?? '') <= toMonth))
      .reduce((sum, s) => sum + (s.count ?? 0), 0);
  }

  /** Grand total of messages across every "archived" (month) partition. */
  grandTotal(): number {
    const stats = this.messagesDataService.partitionStats()?.statsPerMonth ?? [];
    return stats.reduce((sum, s) => sum + (s.count ?? 0), 0);
  }

}
