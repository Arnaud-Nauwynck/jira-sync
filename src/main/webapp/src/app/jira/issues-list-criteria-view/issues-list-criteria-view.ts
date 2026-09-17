import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule } from '@ng-bootstrap/ng-bootstrap';
import { IssuesCriteriaDTO } from '../../rest';
import { TextContainsFilter } from '../issues-search-page/filters/text-contains-filter';
import { NumberRangeFilter } from '../issues-search-page/filters/number-range-filter';
import { DateRangeFilter } from '../issues-search-page/filters/date-range-filter';
import { AvailabilityFilter, AvailabilityFilterComponent } from '../issues-search-page/filters/availability-filter';
import { ExcludeDropdownFilter, ExcludeFilterOption } from '../issues-search-page/filters/exclude-dropdown-filter';
import { ExcludeButtonGroupFilter } from '../issues-search-page/filters/exclude-buttongroup-filter';

const OTHER_RESOLUTIONS = '(others)';
const OTHER_TYPES = '(others)';

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
export class IssuesListCriteriaView {

  /** Sent as-is to the server (POST .../query), and reused client-side to re-filter already-fetched rows. */
  readonly criteria: IssuesCriteriaDTO = {
    fromYear: 2020,
    toYear: 2050,
    pullRequestAvailableLabel: 'any',
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

  onFieldChanged() {
    this.criteriaChanged.emit();
  }

  onSearch() {
    this.search.emit();
  }

  onExcludedTypesChange(excluded: Set<string>) {
    this.excludedTypes = excluded;
    this.criteria.excludedTypes = Array.from(excluded).join(',');
    this.onFieldChanged();
  }

  onExcludedResolutionsChange(excluded: Set<string>) {
    this.excludedResolutions = excluded;
    this.criteria.excludedResolutions = Array.from(excluded).join(',');
    this.onFieldChanged();
  }

  onExcludedStatusesChange(excluded: Set<string>) {
    this.excludedStatuses = excluded;
    this.criteria.excludedStatuses = Array.from(excluded).join(',');
    this.onFieldChanged();
  }

  onExcludedPrioritiesChange(excluded: Set<string>) {
    this.excludedPriorities = excluded;
    this.criteria.excludedPriorities = Array.from(excluded).join(',');
    this.onFieldChanged();
  }

  /** Narrows the DTO's plain `string` tri-state field to the widget's {@link AvailabilityFilter} type. */
  availabilityOf(value: string | undefined): AvailabilityFilter {
    return value === 'yes' || value === 'no' ? value : 'any';
  }

}
