import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule } from '@ng-bootstrap/ng-bootstrap';
import { IssuesCriteriaDTO } from '../../rest';
import { TextContainsFilter } from '../issues-search-page/filters/text-contains-filter';
import { NumberRangeFilter } from '../issues-search-page/filters/number-range-filter';
import { DateRangeFilter } from '../issues-search-page/filters/date-range-filter';
import { AvailabilityFilter, AvailabilityFilterComponent } from '../issues-search-page/filters/availability-filter';
import { ExcludeDropdownFilter, ExcludeFilterOption } from '../issues-search-page/filters/exclude-dropdown-filter';
import { ExcludeButtonGroupFilter } from '../issues-search-page/filters/exclude-buttongroup-filter';
import { csvToSet, setToCsv } from '../../utils/csv-set';
import { clearCriteriaFields } from '../../utils/clear-criteria';
import { escapeRegExp } from '../../utils/regex-escape';

const OTHER_RESOLUTIONS = '(others)';
const OTHER_TYPES = '(others)';

const RECENT_LIMIT = 50;

/** Mode of the shrinked "Search Criteria" panel: a quick default listing, a lookup by issue key,
 * or the full advanced criteria panel. */
export type IssuesSearchMode = 'recent' | 'byId' | 'advanced';

/** Owns the Data Fetching/Main/Analysis/Development Work/Personal Interest filter criteria of the
 * issues-list page, as an {@link IssuesCriteriaDTO} sent as-is to the server. */
@Component({
  imports: [
    FormsModule, NgbCollapseModule,
    TextContainsFilter, NumberRangeFilter, DateRangeFilter, AvailabilityFilterComponent,
    ExcludeDropdownFilter, ExcludeButtonGroupFilter,
  ],
  selector: 'app-issues-list-criteria-view',
  templateUrl: './issues-list-criteria-view.html',
})
export class IssuesListCriteriaView implements OnInit {

  /** Owned by the parent page, edited in-place here: sent as-is to the server (POST .../query),
   * and reused client-side to re-filter already-fetched rows. */
  @Input({ required: true }) criteria!: IssuesCriteriaDTO;

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
  @Output() readonly searchModeChange = new EventEmitter<IssuesSearchMode>();

  // Mode of the shrinked panel: "recent" (default quick listing), "by id" (lookup by issue key) or
  // "advanced" (expands the full criteria panel below).
  searchMode: IssuesSearchMode = 'recent';

  // "By id" input: a bare number ("1234") looks up the issue number, a full key ("SPARK-1234")
  // looks up that key exactly; neither is treated as a regex (see onIdValueChanged()).
  idValue = '';

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

  // Status enum filter: kept locally as a Set for the exclude widgets, serialized as CSV onto the criteria.
  statusOptions = ['Open', 'In Progress', 'Reopened', 'Resolved', 'Closed'];
  excludedStatuses = new Set<string>();

  // Priority enum filter.
  priorityOptions = ['Critical', 'Blocker', 'Major', 'Minor', 'Trivial'];
  priorityFilterOptions: ExcludeFilterOption[] = this.priorityOptions.map((p) => ({ label: p, value: p }));
  excludedPriorities = new Set<string>();

  // Type enum filter.
  typeOptions = ['Bug', 'Improvement', 'New Feature', 'Story', 'Epic', 'Sub-task', 'Task', 'Umbrella', 'Question',
    'Wish', 'Test', 'Documentation', 'IT Help', 'Brainstorming', 'Dependency upgrade', 'Request',
    'Planned Work', 'Github Integration', 'RTC', 'Blog - New Blog Request',
    OTHER_TYPES
  ];
  typeFilterOptions: ExcludeFilterOption[] = this.typeOptions.map((t) => ({ label: t, value: t }));
  excludedTypes = new Set<string>();

  // Resolution enum filter.
  resolutionOptions: ExcludeFilterOption[] = [
    { label: 'Done', value: 'Done' },
    { label: 'Fixed', value: 'Fixed' },
    { label: 'Invalid', value: 'Invalid' },
    { label: 'Incomplete', value: 'Incomplete' },
    { label: 'Cannot Reproduce', value: 'Cannot Reproduce' },
    { label: 'Works for Me', value: 'Works for Me' },
    { label: 'Not A Problem', value: 'Not A Problem' },
    { label: "Won't Fix", value: "Won't Fix" },
    { label: "Won't Do", value: "Won't Do" },
    { label: "Later", value: "Later" },
    { label: 'Duplicate', value: 'Duplicate' },
    { label: 'Resolved', value: 'Resolved' },
    { label: 'Not A Bug', value: 'Not A Bug' },
    { label: 'Abandoned', value: 'Abandoned' },
    { label: 'Auto Closed', value: 'Auto Closed' },
    { label: 'WorkAround', value: 'WorkAround' },
    { label: 'Workaround', value: 'Workaround' },
    { label: 'Implemented', value: 'Implemented' },
    { label: 'Information Provided', value: 'Information Provided' },
    { label: '_', value: '' },
    { label: '(other)', value: OTHER_RESOLUTIONS },
  ];
  excludedResolutions = new Set<string>();

  /** Rebuilds the widgets' local Sets from the bound criteria: the criteria outlives this view (it is
   * held by the data service, and restored from the URL), so the Sets cannot be defaulted blindly. */
  ngOnInit() {
    this.syncExcludedSetsFromCriteria();
  }

  private syncExcludedSetsFromCriteria() {
    this.excludedStatuses = csvToSet(this.criteria.excludedStatuses);
    this.excludedPriorities = csvToSet(this.criteria.excludedPriorities);
    this.excludedTypes = csvToSet(this.criteria.excludedTypes);
    this.excludedResolutions = csvToSet(this.criteria.excludedResolutions);
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
  onModeChange(mode: IssuesSearchMode) {
    this.searchMode = mode;
    this.searchModeChange.emit(mode);
    if (mode === 'advanced') {
      this.isCriteriaCollapsed = false;
      return;
    }
    this.isCriteriaCollapsed = true;
    clearCriteriaFields(this.criteria);
    this.syncExcludedSetsFromCriteria();
    this.idValue = '';
    this.onFieldChanged();
    if (mode === 'recent') {
      this.setLimit(RECENT_LIMIT);
      this.onSearch();
    }
  }

  /** Interprets the "by id" input as an exact lookup, never as a regex: a bare number ("1234") is
   * matched against the issue number, anything else ("SPARK-1234") is matched exactly against the key. */
  onIdValueChanged(value: string) {
    this.idValue = value;
    const trimmed = value.trim();
    if (/^\d+$/.test(trimmed)) {
      this.criteria.fromNumber = Number(trimmed);
      this.criteria.toNumber = Number(trimmed);
      this.criteria.keyPattern = undefined;
    } else {
      this.criteria.keyPattern = trimmed ? escapeRegExp(trimmed) : undefined;
      this.criteria.fromNumber = undefined;
      this.criteria.toNumber = undefined;
    }
    this.onFieldChanged();
  }

  /** Triggered from the "by id" input: re-fetches with just the typed id lookup. */
  onIdSearch() {
    this.onSearch();
  }

  private setLimit(limit: number) {
    this.limit = limit;
    this.limitChange.emit(limit);
  }

  onExcludedTypesChange(excluded: Set<string>) {
    this.excludedTypes = excluded;
    this.criteria.excludedTypes = setToCsv(excluded);
    this.onFieldChanged();
  }

  onExcludedResolutionsChange(excluded: Set<string>) {
    this.excludedResolutions = excluded;
    this.criteria.excludedResolutions = setToCsv(excluded);
    this.onFieldChanged();
  }

  onExcludedStatusesChange(excluded: Set<string>) {
    this.excludedStatuses = excluded;
    this.criteria.excludedStatuses = setToCsv(excluded);
    this.onFieldChanged();
  }

  onExcludedPrioritiesChange(excluded: Set<string>) {
    this.excludedPriorities = excluded;
    this.criteria.excludedPriorities = setToCsv(excluded);
    this.onFieldChanged();
  }

  /** Narrows the DTO's plain `string` tri-state field to the widget's {@link AvailabilityFilter} type. */
  availabilityOf(value: string | undefined): AvailabilityFilter {
    return value === 'yes' || value === 'no' ? value : 'any';
  }

}
