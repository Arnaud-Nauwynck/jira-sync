import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule } from '@ng-bootstrap/ng-bootstrap';
import { MailMessageCriteriaDTO } from '../../rest';
import { TextContainsFilter } from '../../jira/issues-search-page/filters/text-contains-filter';
import { NumberRangeFilter } from '../../jira/issues-search-page/filters/number-range-filter';
import { DateRangeFilter } from '../../jira/issues-search-page/filters/date-range-filter';
import { AvailabilityFilter, AvailabilityFilterComponent } from '../../jira/issues-search-page/filters/availability-filter';

/** Owns the Data Fetching/Main/Analysis/Development Work/Personal Interest filter criteria of the
 * mailing-list page, as a {@link MailMessageCriteriaDTO} sent as-is to the server. */
@Component({
  imports: [FormsModule, NgbCollapseModule, TextContainsFilter, NumberRangeFilter, DateRangeFilter, AvailabilityFilterComponent],
  selector: 'app-mail-message-criteria-view',
  templateUrl: './mail-message-criteria-view.html',
})
export class MailMessageCriteriaView {

  /** Sent as-is to the server (POST .../query), and reused client-side to re-filter already-fetched rows. */
  readonly criteria: MailMessageCriteriaDTO = {
    analysisAvailability: 'any',
    developmentWorkAvailability: 'any',
    personalInterrestAvailability: 'any',
  };

  /** Emitted when the "Search" button is clicked, to re-fetch from the server. */
  @Output() readonly search = new EventEmitter<void>();
  /** Emitted on every criteria field change, so the grid can instantly re-apply its client-side filter. */
  @Output() readonly criteriaChanged = new EventEmitter<void>();

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

  /** Narrows the DTO's plain `string` tri-state field to the widget's {@link AvailabilityFilter} type. */
  availabilityOf(value: string | undefined): AvailabilityFilter {
    return value === 'yes' || value === 'no' ? value : 'any';
  }

}
