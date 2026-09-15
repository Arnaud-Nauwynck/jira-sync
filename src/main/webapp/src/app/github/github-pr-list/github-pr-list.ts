import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { GitHubPullRequestDTO } from '../../rest';
import { GithubPullRequestsDataService } from './github-pull-requests-data.service';
import { GithubPrView } from '../github-pr-view/github-pr-view';
import { TextContainsFilter } from '../../jira/issues-list/filters/text-contains-filter';
import { NumberRangeFilter } from '../../jira/issues-list/filters/number-range-filter';
import { DateRangeFilter } from '../../jira/issues-list/filters/date-range-filter';
import { AvailabilityFilterComponent } from '../../jira/issues-list/filters/availability-filter';

/** Tri-state availability/boolean filter: 'no' = false, 'yes' = true, 'any' = no filtering. */
export type AvailabilityFilter = 'no' | 'any' | 'yes';

@Component({
  imports: [
    AgGridAngular, FormsModule, NgbDropdownModule, NgbCollapseModule, GithubPrView,
    TextContainsFilter, NumberRangeFilter, DateRangeFilter, AvailabilityFilterComponent,
  ],
  selector: 'app-github-pr-list',
  templateUrl: './github-pr-list.html',
})
export class GithubPRList implements OnInit {

  // The pull request currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedPullRequest = signal<GitHubPullRequestDTO | undefined>(undefined);

  // Data fetching panel: collapsible, expanded by default.
  isDataFetchingCollapsed = false;
  fromYear = 2020;
  toYear = 2050;
  usernamePattern = '';
  fromPullRequestNumber: number | null = null;
  toPullRequestNumber: number | null = null;
  pullRequestNumberPattern = '';

  // Main criteria panel: collapsible, expanded by default.
  isMainCriteriaCollapsed = false;

  // Row filter criteria (client-side, applied via ag-grid external filter).
  titleContains = '';
  bodyContains = '';
  authorContains = '';
  labelContains = '';
  baseRefContains = '';

  // State enum filter: clicking a state button excludes that state from the results.
  stateOptions = ['open', 'closed'];
  excludedStates = new Set<string>();

  draftAvailability: AvailabilityFilter = 'any';
  mergedAvailability: AvailabilityFilter = 'any';

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

  // Column Definitions: Defines the columns to be displayed.
  colDefs: ColDef<GitHubPullRequestDTO>[] = [
    { headerName: 'Number', field: 'number', width: 100,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      onCellClicked: (params: CellClickedEvent<GitHubPullRequestDTO>) => {
        if (params.data) {
          this.selectedPullRequest.set(params.data);
        }
      },
    },
    { headerName: 'State', field: 'state', width: 90 },
    { headerName: 'Draft', field: 'draft', width: 80, cellDataType: 'boolean' },
    { headerName: 'Merged', field: 'merged', width: 90, cellDataType: 'boolean' },
    { headerName: 'Title', field: 'title', width: 400 },
    { headerName: 'Body', field: 'body', width: 250, hide: true },
    { headerName: 'Author', field: 'authorLogin', width: 120 },
    { headerName: 'Assignees', width: 150,
      valueGetter: (params) => (params.data?.assigneeLogins ?? []).join(', '),
    },
    { headerName: 'Requested Reviewers', width: 160,
      valueGetter: (params) => (params.data?.requestedReviewerLogins ?? []).join(', '),
    },
    { headerName: 'Labels', width: 180,
      valueGetter: (params) => (params.data?.labelNames ?? []).join(', '),
    },
    { headerName: 'Milestone', field: 'milestoneTitle', width: 120 },
    { headerName: 'Created', field: 'createdAt', width: 100 },
    { headerName: 'Updated', field: 'updatedAt', width: 100 },
    { headerName: 'Closed', field: 'closedAt', width: 100, hide: true },
    { headerName: 'Merged At', field: 'mergedAt', width: 100, hide: true },
    { headerName: 'Head Ref', field: 'headRef', width: 140 },
    { headerName: 'Base Ref', field: 'baseRef', width: 120 },
    { headerName: 'Mergeable', field: 'mergeable', width: 100, cellDataType: 'boolean', hide: true },
    { headerName: 'Mergeable State', field: 'mergeableState', width: 130, hide: true },
    { headerName: 'Merged By', field: 'mergedByLogin', width: 120, hide: true },
    { headerName: 'Comments', field: 'comments', width: 100 },
    { headerName: 'Review Comments', field: 'reviewComments', width: 130, hide: true },
    { headerName: 'Commits', field: 'commits', width: 90, hide: true },
    { headerName: 'Additions', field: 'additions', width: 100, hide: true },
    { headerName: 'Deletions', field: 'deletions', width: 100, hide: true },
    { headerName: 'Changed Files', field: 'changedFiles', width: 110, hide: true },
  ];

  private gridApi?: GridApi<GitHubPullRequestDTO>;

  constructor(readonly pullRequestsDataService: GithubPullRequestsDataService, private router: Router) {}

  ngOnInit() {
    this.search();
  }

  onGridReady(event: GridReadyEvent<GitHubPullRequestDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  closeDetail() {
    this.selectedPullRequest.set(undefined);
  }

  openDetailAsRoute() {
    const number = this.selectedPullRequest()?.number;
    if (number != null) {
      this.router.navigate(['/github-pull-request', number]);
    }
  }

  toggleStateFilter(state: string) {
    if (this.excludedStates.has(state)) {
      this.excludedStates.delete(state);
    } else {
      this.excludedStates.add(state);
    }
    this.onFilterInputsChanged();
  }

  isStateExcluded(state: string): boolean {
    return this.excludedStates.has(state);
  }

  isExternalFilterPresent = (): boolean => {
    return this.fromYear != null
      || this.toYear != null
      || this.usernamePattern.trim().length > 0
      || this.fromPullRequestNumber != null
      || this.toPullRequestNumber != null
      || this.pullRequestNumberPattern.trim().length > 0
      || this.parseCsvList(this.titleContains).length > 0
      || this.parseCsvList(this.bodyContains).length > 0
      || this.parseCsvList(this.authorContains).length > 0
      || this.parseCsvList(this.labelContains).length > 0
      || this.parseCsvList(this.baseRefContains).length > 0
      || this.excludedStates.size > 0
      || this.draftAvailability !== 'any'
      || this.mergedAvailability !== 'any'
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

  doesExternalFilterPass = (node: IRowNode<GitHubPullRequestDTO>): boolean => {
    const pr = node.data;
    if (!pr) {
      return true;
    }
    if (!this.matchesYearRange(pr.createdAt)) {
      return false;
    }
    if (!this.matchesUsernamePattern(this.usernamePattern, pr.authorLogin)) {
      return false;
    }
    if (!this.matchesPullRequestNumberPattern(this.pullRequestNumberPattern, pr.number)) {
      return false;
    }
    if (!this.matchesPullRequestNumberRange(this.fromPullRequestNumber, this.toPullRequestNumber, pr.number)) {
      return false;
    }
    if (!this.matchesAny(this.titleContains, pr.title)) {
      return false;
    }
    if (!this.matchesAny(this.bodyContains, pr.body)) {
      return false;
    }
    if (!this.matchesAny(this.authorContains, pr.authorLogin, ...(pr.assigneeLogins ?? []), ...(pr.requestedReviewerLogins ?? []))) {
      return false;
    }
    if (!this.matchesAny(this.labelContains, ...(pr.labelNames ?? []))) {
      return false;
    }
    if (!this.matchesAny(this.baseRefContains, pr.baseRef)) {
      return false;
    }
    if (pr.state != null && this.excludedStates.has(pr.state)) {
      return false;
    }
    if (!this.matchesAvailability(this.draftAvailability, !!pr.draft)) {
      return false;
    }
    if (!this.matchesAvailability(this.mergedAvailability, !!pr.merged)) {
      return false;
    }
    const annotated = pr.annotated;
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

  /** Whether the createdAt date's year falls within [fromYear, toYear] (inclusive); unparsable/missing dates pass through. */
  private matchesYearRange(createdAt: string | undefined): boolean {
    if (createdAt == null) {
      return true;
    }
    const year = parseInt(createdAt.substring(0, 4), 10);
    if (isNaN(year)) {
      return true;
    }
    return year >= this.fromYear && year <= this.toYear;
  }

  /** Matches the given regex (full match) against the author login, falling back to "unknown", mirroring the server-side filter. */
  private matchesUsernamePattern(patternText: string, authorLogin: string | undefined): boolean {
    const name = (authorLogin && authorLogin.trim()) ? authorLogin : 'unknown';
    return this.matchesRegex(patternText, name);
  }

  private matchesPullRequestNumberPattern(patternText: string, number: number | undefined): boolean {
    return this.matchesRegex(patternText, number != null ? String(number) : undefined);
  }

  /** Whether the PR number falls within [fromNumber, toNumber] (inclusive, either bound optional). */
  private matchesPullRequestNumberRange(fromNumber: number | null, toNumber: number | null, number: number | undefined): boolean {
    if (fromNumber == null && toNumber == null) {
      return true;
    }
    if (number == null) {
      return false;
    }
    if (fromNumber != null && number < fromNumber) {
      return false;
    }
    if (toNumber != null && number > toNumber) {
      return false;
    }
    return true;
  }

  /** Full-match regex test; a blank pattern always matches, and an invalid regex is treated as no filter. */
  private matchesRegex(patternText: string, value: string | undefined): boolean {
    const text = (patternText ?? '').trim();
    if (!text) {
      return true;
    }
    if (value == null) {
      return false;
    }
    try {
      return new RegExp(`^(?:${text})$`).test(value);
    } catch {
      return true;
    }
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
    this.pullRequestsDataService.search(this.fromYear, this.toYear, this.usernamePattern,
      this.fromPullRequestNumber, this.toPullRequestNumber, this.pullRequestNumberPattern);
  }

}
