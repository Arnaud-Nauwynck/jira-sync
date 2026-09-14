import { Component, OnInit, signal } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { GitHubPullRequestDTO } from '../rest/model/gitHubPullRequestDTO';
import { GithubPullRequestsDataService } from './github-pull-requests-data.service';

/** Tri-state availability/boolean filter: 'no' = false, 'yes' = true, 'any' = no filtering. */
export type AvailabilityFilter = 'no' | 'any' | 'yes';

@Component({
  imports: [AgGridAngular, FormsModule, NgbDropdownModule, NgbCollapseModule],
  selector: 'app-github-pr-list',
  templateUrl: './github-pr-list.html',
})
export class GithubPRList implements OnInit {

  // Data fetching panel: collapsible, expanded by default.
  isDataFetchingCollapsed = false;
  fromYear = 2020;
  toYear = 2050;
  usernamePattern = '';

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

  // Column Definitions: Defines the columns to be displayed.
  colDefs: ColDef<GitHubPullRequestDTO>[] = [
    { headerName: 'Number', field: 'number', width: 100,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      onCellClicked: (params: CellClickedEvent<GitHubPullRequestDTO>) => {
        const url = params.data?.htmlUrl;
        if (url) {
          window.open(url, '_blank', 'noopener');
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

  constructor(readonly pullRequestsDataService: GithubPullRequestsDataService) {}

  ngOnInit() {
    this.search();
  }

  onGridReady(event: GridReadyEvent<GitHubPullRequestDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
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
    return this.parseCsvList(this.titleContains).length > 0
      || this.parseCsvList(this.bodyContains).length > 0
      || this.parseCsvList(this.authorContains).length > 0
      || this.parseCsvList(this.labelContains).length > 0
      || this.parseCsvList(this.baseRefContains).length > 0
      || this.excludedStates.size > 0
      || this.draftAvailability !== 'any'
      || this.mergedAvailability !== 'any';
  };

  doesExternalFilterPass = (node: IRowNode<GitHubPullRequestDTO>): boolean => {
    const pr = node.data;
    if (!pr) {
      return true;
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

  private parseCsvList(value: string): string[] {
    return (value ?? '')
      .split(',')
      .map((term) => term.trim())
      .filter((term) => term.length > 0);
  }

  search() {
    this.pullRequestsDataService.search(this.fromYear, this.toYear, this.usernamePattern);
  }

}
