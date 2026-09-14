import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, ColGroupDef, GridApi, GridReadyEvent, ICellRendererParams, IRowNode, ValueGetterParams } from 'ag-grid-community';
import { FormsModule } from '@angular/forms';
import { JiraIssuesService } from '../rest/api/jiraIssues.service';
import { UserIssueCreatePerYearStatsDTO } from '../rest/model/userIssueCreatePerYearStatsDTO';
import {UserJiraIssueStatsDTO} from '../rest';
import { IssueDetails } from '../issue-details/issue-details';

interface PerYearStatDef {
  key: keyof UserIssueCreatePerYearStatsDTO;
  header: string;
  width: number;
}

const PER_YEAR_STAT_DEFS: PerYearStatDef[] = [
  { key: 'issueCreateCount', header: 'Issues', width: 90 },
  { key: 'openIssuesCount', header: 'Open', width: 90 },
  { key: 'closedIssuesCount', header: 'Closed', width: 90 },
  { key: 'closedBySelfIssuesCount', header: 'Closed (Self)', width: 100 },
  { key: 'closedForOtherIssuesCount', header: 'Closed (Other)', width: 100 },
  { key: 'rejectedIssuesCount', header: 'Rejected', width: 90 },
  { key: 'reopenedIssuesCount', header: 'Reopened', width: 90 },
  { key: 'issuesCommentsCount', header: 'Comments', width: 100 },
];

@Component({
  imports: [AgGridAngular, FormsModule, IssueDetails],
  selector: 'app-user-issue-stat-list',
  templateUrl: './user-issue-stat-list.html',
})
export class UserIssueStatList implements OnInit {

  // The issue key currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedIssueKey = signal<string | undefined>(undefined);

  fromYear = 2012;
  toYear = 2030;
  usernamePattern = '';

  // Server-side issue criteria (sent as query params to search()).
  summaryPattern = '';
  descriptionPattern = '';
  commentPattern = '';
  commentAuthorPattern = '';

  // Row filter criteria (client-side, applied via ag-grid external filter).
  minCreatedIssues: number | null = null;
  maxCreatedIssues: number | null = null;
  nameContains = '';
  namePattern = '';

  // Row Data: The data to be displayed.
  rowData = signal<UserJiraIssueStatsDTO[]>([]);

  // Column Definitions: Defines the columns to be displayed.
  colDefs = signal<(ColDef<UserJiraIssueStatsDTO>)[]>([]);

  private gridApi?: GridApi<UserJiraIssueStatsDTO>;

  constructor(private jiraIssuesService: JiraIssuesService, private router: Router) {}

  ngOnInit() {
    this.search();
  }

  onGridReady(event: GridReadyEvent<UserJiraIssueStatsDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  closeDetail() {
    this.selectedIssueKey.set(undefined);
  }

  openDetailAsRoute() {
    const key = this.selectedIssueKey();
    if (key) {
      this.router.navigate(['/issue', key]);
    }
  }

  isExternalFilterPresent = (): boolean => {
    return this.minCreatedIssues != null
      || this.maxCreatedIssues != null
      || this.parseCsvList(this.nameContains).length > 0
      || this.parseCsvList(this.namePattern).length > 0;
  };

  doesExternalFilterPass = (node: IRowNode<UserJiraIssueStatsDTO>): boolean => {
    const data = node.data;
    if (!data) {
      return true;
    }
    const issueCreateCount = data.issueCreateCount ?? 0;
    if (this.minCreatedIssues != null && issueCreateCount < this.minCreatedIssues) {
      return false;
    }
    if (this.maxCreatedIssues != null && issueCreateCount > this.maxCreatedIssues) {
      return false;
    }
    const user = data.user ?? '';
    const containsTerms = this.parseCsvList(this.nameContains);
    if (containsTerms.length > 0
        && !containsTerms.some((term) => user.toLowerCase().includes(term.toLowerCase()))) {
      return false;
    }
    const patterns = this.parseCsvList(this.namePattern);
    if (patterns.length > 0
        && !patterns.some((pattern) => this.matchesPattern(user, pattern))) {
      return false;
    }
    return true;
  };

  private orUndefined(value: string): string | undefined {
    return value != null && value.trim().length > 0 ? value : undefined;
  }

  private parseCsvList(value: string): string[] {
    return (value ?? '')
      .split(',')
      .map((term) => term.trim())
      .filter((term) => term.length > 0);
  }

  private matchesPattern(value: string, pattern: string): boolean {
    try {
      return new RegExp(pattern, 'i').test(value);
    } catch {
      return false;
    }
  }

  search() {
    const fromYear = this.fromYear;
    const toYear = this.toYear;
    this.colDefs.set(this.buildColDefs(fromYear, toYear));
    this.jiraIssuesService.queryUserIssueCreateStats(this.usernamePattern, fromYear, toYear,
        this.orUndefined(this.summaryPattern), this.orUndefined(this.descriptionPattern),
        this.orUndefined(this.commentPattern), this.orUndefined(this.commentAuthorPattern),
        'body', false, { httpHeaderAccept: 'application/json' as any })
      .subscribe({
        next: (stats) => {
          this.rowData.set(stats);
        },
        error: (err) => {
          console.error('failed to load user issue create stats', err)
        },
      });
  }

  private buildColDefs(fromYear: number, toYear: number): ColDef<UserJiraIssueStatsDTO>[] {
    const perYearColDefs: ColGroupDef<UserJiraIssueStatsDTO>[] = [];
    for (let year = fromYear; year <= toYear; year++) {
      const yearColumns: ColDef<UserJiraIssueStatsDTO>[] = [
        ...PER_YEAR_STAT_DEFS.map((stat) => ({
          headerName: stat.header,
          width: stat.width,
          valueGetter: (params: ValueGetterParams<UserJiraIssueStatsDTO>) => params.data?.perYear?.[year]?.[stat.key] ?? 0,
        })),
        {
          headerName: 'First Issues',
          width: 160,
          valueGetter: (params: ValueGetterParams<UserJiraIssueStatsDTO>) => (params.data?.perYear?.[year]?.firstIssues ?? []).join(', '),
          cellRenderer: (params: ICellRendererParams<UserJiraIssueStatsDTO>) => {
            const keys = ((params.value as string) ?? '').split(',').map((k) => k.trim()).filter((k) => k.length > 0);
            return keys
              .map((key) => `<span class="issue-key-link" data-issue-key="${key}" style="cursor:pointer;text-decoration:underline;color:var(--bs-link-color,#0d6efd);margin-right:8px;">${key}</span>`)
              .join('');
          },
          onCellClicked: (params: CellClickedEvent<UserJiraIssueStatsDTO>) => {
            const target = params.event?.target as HTMLElement | null;
            const key = target?.closest<HTMLElement>('[data-issue-key]')?.dataset['issueKey'];
            if (key) {
              this.selectedIssueKey.set(key);
            }
          },
        },
      ];
      perYearColDefs.push({
        headerName: `${year}`,
        children: yearColumns,
      });
    }
    return [
      { field: 'user', width: 150 },
      { headerName: 'Issues Created', width: 100, field: 'issueCreateCount', },
      { headerName: 'Open', width: 100, field: 'openIssuesCount', },
      { headerName: 'Closed', width: 100, field: 'closedIssuesCount', },
      { headerName: 'Closed (Self)', width: 110, field: 'closedBySelfIssuesCount', },
      { headerName: 'Closed (Other)', width: 110, field: 'closedForOtherIssuesCount', },
      { headerName: 'Rejected', width: 100, field: 'rejectedIssuesCount', },
      { headerName: 'Reopened', width: 100, field: 'reopenedIssuesCount', },
      { headerName: 'Comments', width: 100, field: 'issuesCommentsCount', },
      ...perYearColDefs,
    ];
  }
}
