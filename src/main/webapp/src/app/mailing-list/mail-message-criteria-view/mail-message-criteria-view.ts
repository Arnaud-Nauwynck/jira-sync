import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule } from '@ng-bootstrap/ng-bootstrap';
import { MailMessageCriteriaDTO } from '../../rest';
import { TextContainsFilter } from '../../jira/issues-search-page/filters/text-contains-filter';
import { NumberRangeFilter } from '../../jira/issues-search-page/filters/number-range-filter';
import { DateRangeFilter } from '../../jira/issues-search-page/filters/date-range-filter';
import { AvailabilityFilter, AvailabilityFilterComponent } from '../../jira/issues-search-page/filters/availability-filter';
import { clearCriteriaFields } from '../../utils/clear-criteria';

const RECENT_LIMIT = 50;

/** Mode of the shrinked "Search Criteria" panel: a quick default listing, a lookup by Message-Id,
 * or the full advanced criteria panel. */
export type MailMessageSearchMode = 'recent' | 'byId' | 'advanced';

/** Owns the Data Fetching/Main/Analysis/Development Work/Personal Interest filter criteria of the
 * mailing-list page, as a {@link MailMessageCriteriaDTO} sent as-is to the server. */
@Component({
  imports: [FormsModule, NgbCollapseModule, TextContainsFilter, NumberRangeFilter, DateRangeFilter, AvailabilityFilterComponent],
  selector: 'app-mail-message-criteria-view',
  templateUrl: './mail-message-criteria-view.html',
})
export class MailMessageCriteriaView {

  /** Owned by the parent page, edited in-place here: sent as-is to the server (POST .../query),
   * and reused client-side to re-filter already-fetched rows. */
  @Input({ required: true }) criteria!: MailMessageCriteriaDTO;

  /** True while a search is in-flight: disables the "Search" button to avoid re-entrant queries. */
  @Input() loading = false;

  /** Error of the last failed search, displayed next to the "Search" button, or '' when there is none. */
  @Input() loadErrorMessage = '';

  /** Fetch limit, owned by the parent page: only touched here to force it to {@link RECENT_LIMIT}
   * when the "recent" mode is picked. */
  @Input() limit = 0;
  @Output() readonly limitChange = new EventEmitter<number>();

  /** Emitted when the "Search" button is clicked, to re-fetch from the server. */
  @Output() readonly search = new EventEmitter<void>();
  /** Emitted on every criteria field change, so the grid can instantly re-apply its client-side filter. */
  @Output() readonly criteriaChanged = new EventEmitter<void>();
  /** Emitted whenever the shrinked panel's mode changes, so the page can e.g. auto-open the detail
   * of a unique "by id" match instead of showing the grid. */
  @Output() readonly searchModeChange = new EventEmitter<MailMessageSearchMode>();

  // Mode of the shrinked panel: "recent" (default quick listing), "by id" (lookup by Message-Id) or
  // "advanced" (expands the full criteria panel below).
  searchMode: MailMessageSearchMode = 'recent';

  // Whole search-criteria panel: collapsible, expanded by default.
  isCriteriaCollapsed = false;
  // Data fetching panel: collapsible, expanded by default.
  isDataFetchingCollapsed = false;
  // Main criteria panel: collapsible, expanded by default.
  isMainCriteriaCollapsed = false;
  // Analysis criteria panel: collapsible, collapsed by default.
  isAnalysisCriteriaCollapsed = true;
  // Development work criteria panel: collapsible, collapsed by default.
  isDevelopmentWorkCriteriaCollapsed = true;
  // Personal interest criteria panel: collapsible, collapsed by default.
  isPersonalInterestCriteriaCollapsed = true;

  onFieldChanged() {
    this.criteriaChanged.emit();
  }

  onSearch() {
    this.search.emit();
  }

  /** Switches the shrinked panel's mode: "recent" clears every criteria field and re-searches with a
   * small fixed limit; "by id" clears every field too, leaving just the id lookup to fill in;
   * "advanced" simply re-expands the full panel, untouched. */
  onModeChange(mode: MailMessageSearchMode) {
    this.searchMode = mode;
    this.searchModeChange.emit(mode);
    if (mode === 'advanced') {
      this.isCriteriaCollapsed = false;
      return;
    }
    this.isCriteriaCollapsed = true;
    clearCriteriaFields(this.criteria);
    this.onFieldChanged();
    if (mode === 'recent') {
      this.setLimit(RECENT_LIMIT);
      this.onSearch();
    }
  }

  /** Triggered from the "by id" input: re-fetches with just the typed Message-Id pattern. */
  onIdSearch() {
    this.onFieldChanged();
    this.onSearch();
  }

  private setLimit(limit: number) {
    this.limit = limit;
    this.limitChange.emit(limit);
  }

  /** Narrows the DTO's plain `string` tri-state field to the widget's {@link AvailabilityFilter} type. */
  availabilityOf(value: string | undefined): AvailabilityFilter {
    return value === 'yes' || value === 'no' ? value : 'any';
  }

}
