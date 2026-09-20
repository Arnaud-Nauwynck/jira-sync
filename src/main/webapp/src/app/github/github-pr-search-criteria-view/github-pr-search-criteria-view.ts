import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule } from '@ng-bootstrap/ng-bootstrap';
import { GitHubPrCriteriaDTO } from '../../rest';
import { TextContainsFilter } from '../../jira/issues-search-page/filters/text-contains-filter';
import { NumberRangeFilter } from '../../jira/issues-search-page/filters/number-range-filter';
import { DateRangeFilter } from '../../jira/issues-search-page/filters/date-range-filter';
import { AvailabilityFilter, AvailabilityFilterComponent } from '../../jira/issues-search-page/filters/availability-filter';
import { ExcludeButtonGroupFilter } from '../../jira/issues-search-page/filters/exclude-buttongroup-filter';
import { csvToSet, setToCsv } from '../../utils/csv-set';
import { clearCriteriaFields } from '../../utils/clear-criteria';

const RECENT_LIMIT = 50;

/** Mode of the shrinked "Search Criteria" panel: a quick default listing, a lookup by PR number,
 * or the full advanced criteria panel. */
export type GithubPrSearchMode = 'recent' | 'byId' | 'advanced';

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
export class GithubPrSearchCriteriaView implements OnInit {

  /** Owned by the parent page, edited in-place here: sent as-is to the server (POST .../query),
   * and reused client-side to re-filter already-fetched rows. */
  @Input({ required: true }) criteria!: GitHubPrCriteriaDTO;

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
  @Output() readonly searchModeChange = new EventEmitter<GithubPrSearchMode>();

  // Mode of the shrinked panel: "recent" (default quick listing), "by id" (lookup by PR number) or
  // "advanced" (expands the full criteria panel below).
  searchMode: GithubPrSearchMode = 'recent';

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

  // State enum filter: kept locally as a Set for the exclude-buttongroup-filter widget, serialized as CSV onto the criteria.
  stateOptions = ['open', 'closed'];
  excludedStates = new Set<string>();

  // Mergeable state enum filter: kept locally as a Set for the exclude-buttongroup-filter widget,
  // serialized onto the criteria's `mergeableStatePattern` regex field.
  mergeableStateOptions = ['unknown', 'dirty', 'clean'];
  excludedMergeableStates = new Set<string>();

  /** Rebuilds the widgets' local Sets from the bound criteria: the criteria outlives this view (it is
   * held by the data service, and restored from the URL), so the Sets cannot be defaulted blindly. */
  ngOnInit() {
    this.syncExcludedSetsFromCriteria();
  }

  private syncExcludedSetsFromCriteria() {
    this.excludedStates = csvToSet(this.criteria.excludedStates);
    this.excludedMergeableStates = parseExcludedMergeableStates(this.criteria.mergeableStatePattern);
  }

  onFieldChanged() {
    this.criteriaChanged.emit();
  }

  onSearch() {
    this.search.emit();
  }

  /** Switches the shrinked panel's mode: "recent" clears every criteria field and re-searches with a
   * small fixed limit; "by id" clears every field too, leaving just the id lookup to fill in;
   * "advanced" simply re-expands the full panel, untouched. */
  onModeChange(mode: GithubPrSearchMode) {
    this.searchMode = mode;
    this.searchModeChange.emit(mode);
    if (mode === 'advanced') {
      this.isCriteriaCollapsed = false;
      return;
    }
    this.isCriteriaCollapsed = true;
    clearCriteriaFields(this.criteria);
    this.syncExcludedSetsFromCriteria();
    this.onFieldChanged();
    if (mode === 'recent') {
      this.setLimit(RECENT_LIMIT);
      this.onSearch();
    }
  }

  /** Triggered from the "by id" input: re-fetches with just the typed PR number pattern. */
  onIdSearch() {
    this.onFieldChanged();
    this.onSearch();
  }

  private setLimit(limit: number) {
    this.limit = limit;
    this.limitChange.emit(limit);
  }

  onExcludedStatesChange(excluded: Set<string>) {
    this.excludedStates = excluded;
    this.criteria.excludedStates = setToCsv(excluded);
    this.onFieldChanged();
  }

  onExcludedMergeableStatesChange(excluded: Set<string>) {
    this.excludedMergeableStates = excluded;
    // `mergeableStatePattern` is matched by the server with a full-string regex match, so excluded
    // values are encoded as a negative-lookahead pattern rather than a plain CSV list.
    this.criteria.mergeableStatePattern = excluded.size > 0 ? toExcludingRegexp(excluded) : undefined;
    this.onFieldChanged();
  }

  /** Narrows the DTO's plain `string` tri-state field to the widget's {@link AvailabilityFilter} type. */
  availabilityOf(value: string | undefined): AvailabilityFilter {
    return value === 'yes' || value === 'no' ? value : 'any';
  }

}

/** Encodes the excluded values as a full-string negative-lookahead regexp (the server matches
 * `mergeableStatePattern` as a full-string regexp, it has no "excluded values" CSV field). */
function toExcludingRegexp(excluded: Set<string>): string {
  return `(?!${Array.from(excluded).join('$|')}$).*`;
}

/** Inverse of {@link toExcludingRegexp}, to restore the widget state from a persisted criteria. */
function parseExcludedMergeableStates(pattern: string | undefined): Set<string> {
  const match = /^\(\?!(.*)\)\.\*$/.exec(pattern ?? '');
  if (!match) {
    return new Set();
  }
  return new Set(match[1].split('$|')
    .map((value) => value.endsWith('$') ? value.slice(0, -1) : value)
    .filter((value) => !!value));
}
