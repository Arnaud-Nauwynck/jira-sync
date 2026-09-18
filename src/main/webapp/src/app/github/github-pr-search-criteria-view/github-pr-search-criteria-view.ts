import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule } from '@ng-bootstrap/ng-bootstrap';
import { GitHubPrCriteriaDTO } from '../../rest';
import { TextContainsFilter } from '../../jira/issues-search-page/filters/text-contains-filter';
import { NumberRangeFilter } from '../../jira/issues-search-page/filters/number-range-filter';
import { DateRangeFilter } from '../../jira/issues-search-page/filters/date-range-filter';
import { AvailabilityFilter, AvailabilityFilterComponent } from '../../jira/issues-search-page/filters/availability-filter';
import { ExcludeButtonGroupFilter } from '../../jira/issues-search-page/filters/exclude-buttongroup-filter';

/** Owns the Data Fetching/Main/Analysis/Development Work/Personal Interest filter criteria of the
 * github-pull-requests page, as a {@link GitHubPrCriteriaDTO} sent as-is to the server. */
@Component({
  imports: [
    FormsModule, NgbCollapseModule,
    TextContainsFilter, NumberRangeFilter, DateRangeFilter, AvailabilityFilterComponent, ExcludeButtonGroupFilter,
  ],
  selector: 'app-github-pr-search-criteria-view',
  templateUrl: './github-pr-search-criteria-view.html',
})
export class GithubPrSearchCriteriaView {

  /** Sent as-is to the server (POST .../query), and reused client-side to re-filter already-fetched rows. */
  readonly criteria: GitHubPrCriteriaDTO = {
    fromYear: 2020,
    toYear: 2050,
    draftAvailability: 'any',
    mergedAvailability: 'no',
    mergeableAvailability: 'any',
    analysisAvailability: 'any',
    developmentWorkAvailability: 'any',
    personalInterrestAvailability: 'any',
    excludedStates: 'closed',
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

  // State enum filter: kept locally as a Set for the exclude-buttongroup-filter widget, serialized as CSV onto the criteria.
  stateOptions = ['open', 'closed'];
  excludedStates = new Set<string>(['closed']);

  // Mergeable state enum filter: kept locally as a Set for the exclude-buttongroup-filter widget,
  // serialized onto the criteria's `mergeableStatePattern` regex field.
  mergeableStateOptions = ['unknown', 'dirty', 'clean'];
  excludedMergeableStates = new Set<string>();

  onFieldChanged() {
    this.criteriaChanged.emit();
  }

  onSearch() {
    this.search.emit();
  }

  onExcludedStatesChange(excluded: Set<string>) {
    this.excludedStates = excluded;
    this.criteria.excludedStates = Array.from(excluded).join(',');
    this.onFieldChanged();
  }

  onExcludedMergeableStatesChange(excluded: Set<string>) {
    this.excludedMergeableStates = excluded;
    // `mergeableStatePattern` is matched by the server with a full-string regex match, so excluded
    // values are encoded as a negative-lookahead pattern rather than a plain CSV list.
    this.criteria.mergeableStatePattern = excluded.size > 0 ? `(?!${Array.from(excluded).join('$|')}$).*` : undefined;
    this.onFieldChanged();
  }

  /** Narrows the DTO's plain `string` tri-state field to the widget's {@link AvailabilityFilter} type. */
  availabilityOf(value: string | undefined): AvailabilityFilter {
    return value === 'yes' || value === 'no' ? value : 'any';
  }

}
