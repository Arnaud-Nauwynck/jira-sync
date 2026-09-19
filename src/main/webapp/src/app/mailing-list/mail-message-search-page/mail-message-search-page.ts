import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { MailMessageCriteriaDTO, MailMessageDTO } from '../../rest';
import { MailMessagesDataService } from './mail-messages-data.service';
import { MailMessageView } from '../mail-message-view/mail-message-view';
import { MailMessageListView } from '../mail-message-list-view/mail-message-list-view';
import { MailMessageCriteriaView } from '../mail-message-criteria-view/mail-message-criteria-view';
import { SearchStatsBar } from '../../jira/issues-search-page/filters/search-stats-bar';
import { applyQueryParamsToCriteria, criteriaToQueryParams } from '../../utils/criteria-query-params';
import { httpErrorMessage } from '../../utils/http-error-message';
import { toDisplayId } from '../../utils/mail-message-id';

/** Criteria fields to convert back from string to number when read from the URL query params. */
const NUMERIC_CRITERIA_KEYS = ['analysisSummaryMinTokensK', 'analysisSummaryMaxTokensK',
  'developmentWorkMinTokensK', 'developmentWorkMaxTokensK',
  'personalInterrestMinPriority', 'personalInterrestMaxPriority'];

@Component({
  imports: [MailMessageView, MailMessageListView, MailMessageCriteriaView, SearchStatsBar],
  selector: 'app-mail-message-list',
  templateUrl: './mail-message-search-page.html',
})
export class MailMessageSearchPage implements OnInit {

  /** Search criteria, edited in-place by the criteria view, and sent as-is to the server on a search.
   * Held by the root-scoped data service, so it survives leaving and re-entering this page. */
  get criteria(): MailMessageCriteriaDTO {
    return this.messagesDataService.criteria;
  }

  /** Cap passed to the server on a search: held by the data service, alongside the criteria. */
  get limit(): number {
    return this.messagesDataService.limit;
  }
  set limit(limit: number) {
    this.messagesDataService.limit = limit;
  }

  // The message currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedMessage = signal<MailMessageDTO | undefined>(undefined);

  // True while a server search is in-flight, to disable the "Search" button.
  loading = false;

  // Error of the last failed search, displayed next to the "Search" button, or '' when the last search succeeded.
  loadErrorMessage = '';

  private readonly destroyRef = inject(DestroyRef);

  constructor(
    readonly messagesDataService: MailMessagesDataService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    const limitParam = applyQueryParamsToCriteria(this.criteria, this.route.snapshot.queryParams, NUMERIC_CRITERIA_KEYS);
    if (limitParam) {
      this.limit = limitParam;
    }
    // Re-use the rows already cached by the data service when they match: re-entering the page is then instant.
    if (!this.messagesDataService.isUpToDate(this.criteria, this.limit)) {
      this.search();
    }
    this.messagesDataService.loadPartitionStats();
  }

  closeDetail() {
    this.selectedMessage.set(undefined);
  }

  openDetailAsRoute() {
    const message = this.selectedMessage();
    if (message?.messageId) {
      this.router.navigate(['/mailing-list-message', toDisplayId(message)]);
    }
  }

  search() {
    this.loading = true;
    this.loadErrorMessage = '';
    this.publishCriteriaAsQueryParams();
    this.messagesDataService.query(this.criteria, this.limit)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.loading = false;
        },
        error: (err) => {
          console.error('failed to search mailing-list messages', err)
          this.loadErrorMessage = httpErrorMessage(err);
          this.loading = false;
        },
      });
  }

  /** Mirrors the searched criteria into the URL, so the search is bookmarkable and survives a reload. */
  private publishCriteriaAsQueryParams() {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: criteriaToQueryParams(this.criteria, this.limit),
      replaceUrl: true,
    });
  }

  /** Sum of the "archived" (month) partition counts falling within the current [fromMonth, toMonth] criteria. */
  availableInRange(): number {
    const stats = this.messagesDataService.partitionStats()?.statsPerMonth ?? [];
    const fromMonth = this.criteria.fromMonth;
    const toMonth = this.criteria.toMonth;
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
