import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { JiraIssueDTO } from '../rest/model/jiraIssueDTO';
import { IssuesDataService } from './issues-data.service';
import { IssueView } from '../issue-view/issue-view';

const OTHER_RESOLUTIONS = '(others)';
const OTHER_TYPES = '(others)';

/** Tri-state availability filter: 'no' = not present, 'yes' = present, 'any' = no filtering. */
export type AvailabilityFilter = 'no' | 'any' | 'yes';

@Component({
  imports: [AgGridAngular, FormsModule, NgbDropdownModule, NgbCollapseModule, IssueView],
  selector: 'app-issues-list',
  templateUrl: './issues-list.html',
})
export class IssuesList implements OnInit {

  // The issue currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedIssue = signal<JiraIssueDTO | undefined>(undefined);

  // Data fetching panel: collapsible, expanded by default.
  isDataFetchingCollapsed = false;
  fromYear = 2020;
  toYear = 2050;
  usernamePattern = '';

  // Main criteria panel: collapsible, expanded by default.
  isMainCriteriaCollapsed = false;

  // Row filter criteria (client-side, applied via ag-grid external filter).
  summaryContains = '';
  descriptionContains = '';
  authorContains = '';
  commentsContains = '';
  commentAuthorContains = '';

  // Analysis criteria panel: collapsible, collapsed by default.
  isAnalysisCriteriaCollapsed = true;
  analysisSummaryContains = '';
  analysisSummaryUpdatedFrom = '';
  analysisSummaryUpdatedTo = '';
  analysisSummaryMinTokensK: number | null = null;
  analysisSummaryMaxTokensK: number | null = null;
  analysisUserExtraPromptsContains = '';
  analysisAvailability: AvailabilityFilter = 'any';

  // Development work criteria panel: collapsible, collapsed by default.
  isDevelopmentWorkCriteriaCollapsed = true;
  developmentWorkDescribedContains = '';
  developmentWorkUpdatedFrom = '';
  developmentWorkUpdatedTo = '';
  developmentWorkMinTokensK: number | null = null;
  developmentWorkMaxTokensK: number | null = null;
  developmentWorkUserExtraPromptsContains = '';
  developmentWorkAvailability: AvailabilityFilter = 'any';

  // Personal interest criteria panel: collapsible, collapsed by default.
  isPersonalInterestCriteriaCollapsed = true;
  personalInterrestCommentContains = '';
  personalInterrestMinPriority: number | null = null;
  personalInterrestMaxPriority: number | null = null;
  personalInterrestAvailability: AvailabilityFilter = 'any';

  // Status enum filter: clicking a status button excludes that status from the results.
  statusOptions = ['Open', 'In Progress', 'Reopened', 'Resolved', 'Closed'];
  excludedStatuses = new Set<string>();

  // Priority enum filter: clicking a priority button excludes that priority from the results.
  priorityOptions = ['Critical', 'Blocker', 'Major', 'Minor', 'Trivial'];
  excludedPriorities = new Set<string>();

  // Type enum filter: clicking a type button excludes that issue type from the results.
  typeOptions = ['Bug', 'Improvement', 'New Feature', 'Story', 'Epic', 'Sub-task', 'Task', 'Umbrella', 'Question',
    'Wish', 'Test', 'Documentation', 'IT Help', 'Brainstorming', 'Dependency upgrade', 'Request',
    'Planned Work', 'Github Integration', 'RTC', 'Blog - New Blog Request',
    OTHER_TYPES
  ];
  private readonly knownTypeValues = new Set(this.typeOptions.filter((t) => t !== OTHER_TYPES));
  excludedTypes = new Set<string>();

  // Resolution enum filter: clicking a resolution button excludes that resolution from the results.
  resolutionOptions = [
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
  private readonly knownResolutionValues = new Set(
    this.resolutionOptions.map((o) => o.value).filter((v) => v !== OTHER_RESOLUTIONS));
  excludedResolutions = new Set<string>();

  // Column Definitions: Defines the columns to be displayed.
  colDefs: ColDef<JiraIssueDTO>[] = [
    { headerName: 'Key', field: 'key', width: 120,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      onCellClicked: (params: CellClickedEvent<JiraIssueDTO>) => {
        if (params.data) {
          this.selectedIssue.set(params.data);
        }
      },
    },
    { headerName: 'Type', field: 'fields.issuetype', width: 105 },
    { headerName: 'Status', field: 'fields.status', width: 80 },
    { headerName: 'Priority', field: 'fields.priority', width: 80  },
    { headerName: 'Resolution', field: 'fields.resolution', width: 95  },
    { headerName: 'Summary', field: 'fields.summary', width: 400},
    { headerName: 'Description', field: 'fields.description', width: 200},
    { headerName: 'Project', hide: true, field: 'fields.project', },
    { headerName: 'Creator', field: 'fields.creator', width: 100 },
    { headerName: 'Reporter', field: 'fields.reporter', width: 100 },
    { headerName: 'Assignee', field: 'fields.assignee', width: 100 },
    { headerName: 'Created', field: 'fields.created', width: 100 },
    { headerName: 'Updated', field: 'fields.updated', width: 100 },
    { headerName: 'Votes', width: 70,
      valueGetter: (params) => {
        const votes = params.data?.fields?.votes;
        return (votes)? votes : '';
      },
    },
    { headerName: 'Watches', width: 70,
      valueGetter: (params) => {
        const watchCount = params.data?.fields?.watchCount;
        return (watchCount)? watchCount : '';
      },
    },
    { headerName: 'Annotated', width: 100, cellDataType: 'boolean',
      valueGetter: (params) => !!params.data?.annotated,
    },
    { headerName: 'Total Tokens', width: 110,
      valueGetter: (params) => {
        const sum = (params.data?.annotated?.analysisSummaryTokensConsumed ?? 0)
          + (params.data?.annotated?.developmentWorkTokensConsumed ?? 0);
        return (sum) ? sum : '';
      },
    },

    { headerName: 'Has Analysis', width: 110, cellDataType: 'boolean',
      valueGetter: (params) => !!params.data?.annotated?.analysisSummary,
    },
    { headerName: 'Analysis Updated', field: 'annotated.analysisSummaryLastUpdateTime', width: 130 },
    { headerName: 'Analysis Tokens', width: 110,
      valueGetter: (params) => {
        const tokens = params.data?.annotated?.analysisSummaryTokensConsumed;
        return (tokens) ? tokens : '';
      },
    },


    { headerName: 'Has Dev Work', width: 110, cellDataType: 'boolean',
      valueGetter: (params) => !!params.data?.annotated?.developmentWorkDescribed,
    },
    { headerName: 'Dev Work Updated', field: 'annotated.developmentWorkLastUpdateTime', width: 130 },
    { headerName: 'Dev Tokens', width: 110,
      valueGetter: (params) => {
        const tokens = params.data?.annotated?.developmentWorkTokensConsumed;
        return (tokens) ? tokens : '';
      },
    },

    { headerName: 'Has Personal Interest', width: 130, cellDataType: 'boolean',
      valueGetter: (params) => !!params.data?.annotated?.personalInterrestComment,
    },
    { headerName: 'Personal Interest Priority', field: 'annotated.personalInterrestPriority10', width: 150 },
  ];

  private gridApi?: GridApi<JiraIssueDTO>;

  constructor(readonly issuesDataService: IssuesDataService, private router: Router) {}

  ngOnInit() {
    this.search();
  }

  closeDetail() {
    this.selectedIssue.set(undefined);
  }

  openDetailAsRoute() {
    const key = this.selectedIssue()?.key;
    if (key) {
      this.router.navigate(['/issue', key]);
    }
  }

  onGridReady(event: GridReadyEvent<JiraIssueDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  toggleStatusFilter(status: string) {
    if (this.excludedStatuses.has(status)) {
      this.excludedStatuses.delete(status);
    } else {
      this.excludedStatuses.add(status);
    }
    this.onFilterInputsChanged();
  }

  isStatusExcluded(status: string): boolean {
    return this.excludedStatuses.has(status);
  }

  togglePriorityFilter(priority: string) {
    if (this.excludedPriorities.has(priority)) {
      this.excludedPriorities.delete(priority);
    } else {
      this.excludedPriorities.add(priority);
    }
    this.onFilterInputsChanged();
  }

  isPriorityExcluded(priority: string): boolean {
    return this.excludedPriorities.has(priority);
  }

  areAllPrioritiesSelected(): boolean {
    return this.excludedPriorities.size === 0;
  }

  toggleAllPriorities() {
    if (this.areAllPrioritiesSelected()) {
      this.priorityOptions.forEach((p) => this.excludedPriorities.add(p));
    } else {
      this.excludedPriorities.clear();
    }
    this.onFilterInputsChanged();
  }

  toggleTypeFilter(type: string) {
    if (this.excludedTypes.has(type)) {
      this.excludedTypes.delete(type);
    } else {
      this.excludedTypes.add(type);
    }
    this.onFilterInputsChanged();
  }

  isTypeExcluded(type: string): boolean {
    return this.excludedTypes.has(type);
  }

  areAllTypesSelected(): boolean {
    return this.excludedTypes.size === 0;
  }

  toggleAllTypes() {
    if (this.areAllTypesSelected()) {
      this.typeOptions.forEach((t) => this.excludedTypes.add(t));
    } else {
      this.excludedTypes.clear();
    }
    this.onFilterInputsChanged();
  }

  /** Maps an issue type to itself if it is a known enum option, or to the "(others)" bucket otherwise. */
  private typeFilterKey(type: string | undefined): string {
    const value = type ?? '';
    return this.knownTypeValues.has(value) ? value : OTHER_TYPES;
  }

  toggleResolutionFilter(resolution: string) {
    if (this.excludedResolutions.has(resolution)) {
      this.excludedResolutions.delete(resolution);
    } else {
      this.excludedResolutions.add(resolution);
    }
    this.onFilterInputsChanged();
  }

  isResolutionExcluded(resolution: string): boolean {
    return this.excludedResolutions.has(resolution);
  }

  areAllResolutionsSelected(): boolean {
    return this.excludedResolutions.size === 0;
  }

  toggleAllResolutions() {
    if (this.areAllResolutionsSelected()) {
      this.resolutionOptions.forEach((o) => this.excludedResolutions.add(o.value));
    } else {
      this.excludedResolutions.clear();
    }
    this.onFilterInputsChanged();
  }

  /** Maps a resolution value to itself if it is a known enum option, or to the "(others)" bucket otherwise. */
  private resolutionFilterKey(resolution: string | undefined): string {
    const value = resolution ?? '';
    return this.knownResolutionValues.has(value) ? value : OTHER_RESOLUTIONS;
  }

  isExternalFilterPresent = (): boolean => {
    return this.parseCsvList(this.summaryContains).length > 0
      || this.parseCsvList(this.descriptionContains).length > 0
      || this.parseCsvList(this.authorContains).length > 0
      || this.parseCsvList(this.commentsContains).length > 0
      || this.excludedResolutions.size > 0
      || this.excludedStatuses.size > 0
      || this.excludedPriorities.size > 0
      || this.excludedTypes.size > 0
      || this.parseCsvList(this.commentAuthorContains).length > 0
      || this.parseCsvList(this.analysisSummaryContains).length > 0
      || this.analysisSummaryUpdatedFrom.length > 0
      || this.analysisSummaryUpdatedTo.length > 0
      || this.analysisSummaryMinTokensK != null
      || this.analysisSummaryMaxTokensK != null
      || this.parseCsvList(this.analysisUserExtraPromptsContains).length > 0
      || this.analysisAvailability !== 'any'
      || this.parseCsvList(this.developmentWorkDescribedContains).length > 0
      || this.developmentWorkUpdatedFrom.length > 0
      || this.developmentWorkUpdatedTo.length > 0
      || this.developmentWorkMinTokensK != null
      || this.developmentWorkMaxTokensK != null
      || this.parseCsvList(this.developmentWorkUserExtraPromptsContains).length > 0
      || this.developmentWorkAvailability !== 'any'
      || this.parseCsvList(this.personalInterrestCommentContains).length > 0
      || this.personalInterrestMinPriority != null
      || this.personalInterrestMaxPriority != null
      || this.personalInterrestAvailability !== 'any';
  };

  doesExternalFilterPass = (node: IRowNode<JiraIssueDTO>): boolean => {
    const fields = node.data?.fields;
    if (!fields) {
      return true;
    }
    if (!this.matchesAny(this.summaryContains, fields.summary)) {
      return false;
    }
    if (!this.matchesAny(this.descriptionContains, fields.description)) {
      return false;
    }
    if (!this.matchesAny(this.authorContains, fields.creator, fields.reporter)) {
      return false;
    }
    if (this.excludedResolutions.has(this.resolutionFilterKey(fields.resolution))) {
      return false;
    }
    if (fields.status != null && this.excludedStatuses.has(fields.status)) {
      return false;
    }
    if (fields.priority != null && this.excludedPriorities.has(fields.priority)) {
      return false;
    }
    if (this.excludedTypes.has(this.typeFilterKey(fields.issuetype))) {
      return false;
    }
    const comments = fields.comments ?? [];
    if (!this.matchesAny(this.commentsContains, ...comments.map((c) => c.body))) {
      return false;
    }
    if (!this.matchesAny(this.commentAuthorContains, ...comments.map((c) => c.author))) {
      return false;
    }
    const annotated = node.data?.annotated;
    if (!this.matchesAvailability(this.analysisAvailability, !!annotated?.analysisSummary)) {
      return false;
    }
    if (!this.matchesAny(this.analysisSummaryContains, annotated?.analysisSummary)) {
      return false;
    }
    if (!this.matchesDateRange(this.analysisSummaryUpdatedFrom, this.analysisSummaryUpdatedTo, annotated?.analysisSummaryLastUpdateTime)) {
      return false;
    }
    if (!this.matchesTokensRangeK(this.analysisSummaryMinTokensK, this.analysisSummaryMaxTokensK, annotated?.analysisSummaryTokensConsumed)) {
      return false;
    }
    if (!this.matchesAny(this.analysisUserExtraPromptsContains, ...(annotated?.analysisUserExtraPrompts ?? []))) {
      return false;
    }
    if (!this.matchesAvailability(this.developmentWorkAvailability, !!annotated?.developmentWorkDescribed)) {
      return false;
    }
    if (!this.matchesAny(this.developmentWorkDescribedContains, annotated?.developmentWorkDescribed)) {
      return false;
    }
    if (!this.matchesDateRange(this.developmentWorkUpdatedFrom, this.developmentWorkUpdatedTo, annotated?.developmentWorkLastUpdateTime)) {
      return false;
    }
    if (!this.matchesTokensRangeK(this.developmentWorkMinTokensK, this.developmentWorkMaxTokensK, annotated?.developmentWorkTokensConsumed)) {
      return false;
    }
    if (!this.matchesAny(this.developmentWorkUserExtraPromptsContains, ...(annotated?.developmentWorkUserExtraPrompts ?? []))) {
      return false;
    }
    if (!this.matchesAvailability(this.personalInterrestAvailability, !!annotated?.personalInterrestComment)) {
      return false;
    }
    if (!this.matchesAny(this.personalInterrestCommentContains, annotated?.personalInterrestComment)) {
      return false;
    }
    if (!this.matchesNumberRange(this.personalInterrestMinPriority, this.personalInterrestMaxPriority, annotated?.personalInterrestPriority10)) {
      return false;
    }
    return true;
  };

  private matchesAvailability(filter: AvailabilityFilter, present: boolean): boolean {
    if (filter === 'yes') {
      return present;
    }
    if (filter === 'no') {
      return !present;
    }
    return true;
  }

  private matchesAny(filterValue: string, ...values: (string | undefined)[]): boolean {
    const terms = this.parseCsvList(filterValue);
    if (terms.length === 0) {
      return true;
    }
    return values.some((value) =>
      value != null && terms.some((term) => value.toLowerCase().includes(term.toLowerCase())));
  }

  private matchesDateRange(from: string, to: string, value: string | undefined): boolean {
    if (!from && !to) {
      return true;
    }
    if (value == null) {
      return false;
    }
    if (from && value < from) {
      return false;
    }
    if (to && value > `${to}T23:59:59`) {
      return false;
    }
    return true;
  }

  private matchesNumberRange(min: number | null, max: number | null, value: number | undefined): boolean {
    if (min == null && max == null) {
      return true;
    }
    if (value == null) {
      return false;
    }
    if (min != null && value < min) {
      return false;
    }
    if (max != null && value > max) {
      return false;
    }
    return true;
  }

  /** min/max are expressed in kilo-tokens (thousands); value is the raw token count. */
  private matchesTokensRangeK(minK: number | null, maxK: number | null, value: number | undefined): boolean {
    if (minK == null && maxK == null) {
      return true;
    }
    if (value == null) {
      return false;
    }
    if (minK != null && value < minK * 1000) {
      return false;
    }
    if (maxK != null && value > maxK * 1000) {
      return false;
    }
    return true;
  }

  private parseCsvList(value: string): string[] {
    return (value ?? '')
      .split(',')
      .map((term) => term.trim())
      .filter((term) => term.length > 0);
  }

  search() {
    this.issuesDataService.search(this.fromYear, this.toYear, this.usernamePattern);
  }

}
