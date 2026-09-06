import { Component, OnInit, signal } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { FormsModule } from '@angular/forms';
import { JiraIssuesService } from '../rest/api/jiraIssues.service';
import { UserIssueCreateStatsDTO } from '../rest/model/userIssueCreateStatsDTO';

@Component({
  imports: [AgGridAngular, FormsModule],
  selector: 'app-user-issue-stat-list',
  templateUrl: './user-issue-stat-list.html',
})
export class UserIssueStatList implements OnInit {

  fromYear = 2012;
  toYear = 2030;
  usernamePattern = '';

  // Row filter criteria (client-side, applied via ag-grid external filter).
  minCreatedIssues: number | null = null;
  maxCreatedIssues: number | null = null;
  nameContains = '';
  namePattern = '';

  // Row Data: The data to be displayed.
  rowData = signal<UserIssueCreateStatsDTO[]>([]);

  // Column Definitions: Defines the columns to be displayed.
  colDefs = signal<ColDef<UserIssueCreateStatsDTO>[]>([]);

  private gridApi?: GridApi<UserIssueCreateStatsDTO>;

  constructor(private jiraIssuesService: JiraIssuesService) {}

  ngOnInit() {
    this.search();
  }

  onGridReady(event: GridReadyEvent<UserIssueCreateStatsDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  isExternalFilterPresent = (): boolean => {
    return this.minCreatedIssues != null
      || this.maxCreatedIssues != null
      || this.parseCsvList(this.nameContains).length > 0
      || this.parseCsvList(this.namePattern).length > 0;
  };

  doesExternalFilterPass = (node: IRowNode<UserIssueCreateStatsDTO>): boolean => {
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

  private buildColDefs(fromYear: number, toYear: number): ColDef<UserIssueCreateStatsDTO>[] {
    const perYearColDefs: ColDef<UserIssueCreateStatsDTO>[] = [];
    for (let year = fromYear; year <= toYear; year++) {
      perYearColDefs.push({
        headerName: `${year}-Issues`,
        width: 120,
        valueGetter: (params) => params.data?.perYear?.[year]?.issueCreateCount ?? 0,
      });
    }
    return [
      { field: 'user', width: 150 },
      { headerName: 'Issues Created', width: 100, field: 'issueCreateCount', },
      ...perYearColDefs,
    ];
  }
}
