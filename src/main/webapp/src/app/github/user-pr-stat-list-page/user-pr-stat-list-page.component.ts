import { Component, OnInit, signal } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, ColGroupDef, GridApi, GridReadyEvent, ICellRendererParams, IRowNode, ValueGetterParams } from 'ag-grid-community';
import { FormsModule } from '@angular/forms';
import { GitHubPullRequestsService } from '../../rest';
import { UserGitHubPullRequestStatsDTO } from '../../rest';
import { UserPullRequestCreatePerYearStatsDTO } from '../../rest';
import { GitHubPullRequestDTO } from '../../rest';
import { GithubPrView } from '../github-pr-view/github-pr-view';

interface PerYearStatDef {
  key: keyof UserPullRequestCreatePerYearStatsDTO;
  header: string;
  width: number;
}

const PER_YEAR_STAT_DEFS: PerYearStatDef[] = [
  { key: 'pullRequestCreateCount', header: 'PRs', width: 90 },
  { key: 'openCount', header: 'Open', width: 90 },
  { key: 'mergedCount', header: 'Merged', width: 90 },
  { key: 'closedNotMergedCount', header: 'Closed (Not Merged)', width: 130 },
  { key: 'draftCount', header: 'Draft', width: 90 },
  { key: 'commentsCount', header: 'Comments', width: 100 },
  { key: 'reviewCommentsCount', header: 'Review Comments', width: 130 },
  { key: 'commitsCount', header: 'Commits', width: 90 },
  { key: 'additionsCount', header: 'Additions', width: 100 },
  { key: 'deletionsCount', header: 'Deletions', width: 100 },
  { key: 'changedFilesCount', header: 'Changed Files', width: 110 },
];

@Component({
  imports: [AgGridAngular, FormsModule, GithubPrView],
  selector: 'app-user-pr-stat-list',
  templateUrl: './user-pr-stat-list-page.component.html',
})
export class UserPrStatListPage implements OnInit {

  // The pull request currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedPullRequest = signal<GitHubPullRequestDTO | undefined>(undefined);

  fromYear = 2012;
  toYear = 2030;
  usernamePattern = '';

  // Row filter criteria (client-side, applied via ag-grid external filter).
  minCreatedPullRequests: number | null = null;
  maxCreatedPullRequests: number | null = null;
  nameContains = '';
  namePattern = '';

  // Row Data: The data to be displayed.
  rowData = signal<UserGitHubPullRequestStatsDTO[]>([]);

  // Column Definitions: Defines the columns to be displayed.
  colDefs = signal<(ColDef<UserGitHubPullRequestStatsDTO>)[]>([]);

  private gridApi?: GridApi<UserGitHubPullRequestStatsDTO>;

  constructor(private gitHubPullRequestsService: GitHubPullRequestsService) {}

  ngOnInit() {
    this.search();
  }

  onGridReady(event: GridReadyEvent<UserGitHubPullRequestStatsDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  closeDetail() {
    this.selectedPullRequest.set(undefined);
  }

  isExternalFilterPresent = (): boolean => {
    return this.minCreatedPullRequests != null
      || this.maxCreatedPullRequests != null
      || this.parseCsvList(this.nameContains).length > 0
      || this.parseCsvList(this.namePattern).length > 0;
  };

  doesExternalFilterPass = (node: IRowNode<UserGitHubPullRequestStatsDTO>): boolean => {
    const data = node.data;
    if (!data) {
      return true;
    }
    const pullRequestCreateCount = data.pullRequestCreateCount ?? 0;
    if (this.minCreatedPullRequests != null && pullRequestCreateCount < this.minCreatedPullRequests) {
      return false;
    }
    if (this.maxCreatedPullRequests != null && pullRequestCreateCount > this.maxCreatedPullRequests) {
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
    this.gitHubPullRequestsService.queryUserPullRequestStats(fromYear, toYear, this.orUndefined(this.usernamePattern),
        'body', false, { httpHeaderAccept: 'application/json' as any })
      .subscribe({
        next: (stats) => {
          this.rowData.set(stats);
        },
        error: (err) => {
          console.error('failed to load user pull request stats', err)
        },
      });
  }

  private showPullRequest(number: number) {
    this.gitHubPullRequestsService.findPullRequestByNumber(number, 'body', false, { httpHeaderAccept: 'application/json' as any })
      .subscribe({
        next: (pr) => {
          this.selectedPullRequest.set(pr);
        },
        error: (err) => {
          console.error('failed to load pull request #' + number, err)
        },
      });
  }

  private buildColDefs(fromYear: number, toYear: number): ColDef<UserGitHubPullRequestStatsDTO>[] {
    const perYearColDefs: ColGroupDef<UserGitHubPullRequestStatsDTO>[] = [];
    for (let year = fromYear; year <= toYear; year++) {
      const yearColumns: ColDef<UserGitHubPullRequestStatsDTO>[] = [
        ...PER_YEAR_STAT_DEFS.map((stat) => ({
          headerName: stat.header,
          width: stat.width,
          valueGetter: (params: ValueGetterParams<UserGitHubPullRequestStatsDTO>) => params.data?.perYear?.[year]?.[stat.key] ?? 0,
        })),
        {
          headerName: 'First PRs',
          width: 160,
          valueGetter: (params: ValueGetterParams<UserGitHubPullRequestStatsDTO>) => (params.data?.perYear?.[year]?.firstPullRequests ?? []).join(', '),
          cellRenderer: (params: ICellRendererParams<UserGitHubPullRequestStatsDTO>) => {
            const numbers = ((params.value as string) ?? '').split(',').map((n) => n.trim()).filter((n) => n.length > 0);
            return numbers
              .map((n) => `<span class="pr-number-link" data-pr-number="${n}" style="cursor:pointer;text-decoration:underline;color:var(--bs-link-color,#0d6efd);margin-right:8px;">#${n}</span>`)
              .join('');
          },
          onCellClicked: (params: CellClickedEvent<UserGitHubPullRequestStatsDTO>) => {
            const target = params.event?.target as HTMLElement | null;
            const number = target?.closest<HTMLElement>('[data-pr-number]')?.dataset['prNumber'];
            if (number) {
              this.showPullRequest(Number(number));
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
      { headerName: 'PRs Created', width: 100, field: 'pullRequestCreateCount', },
      { headerName: 'Open', width: 100, field: 'openCount', },
      { headerName: 'Merged', width: 100, field: 'mergedCount', },
      { headerName: 'Closed (Not Merged)', width: 150, field: 'closedNotMergedCount', },
      { headerName: 'Draft', width: 100, field: 'draftCount', },
      { headerName: 'Comments', width: 100, field: 'commentsCount', },
      { headerName: 'Review Comments', width: 130, field: 'reviewCommentsCount', },
      { headerName: 'Commits', width: 100, field: 'commitsCount', },
      { headerName: 'Additions', width: 100, field: 'additionsCount', },
      { headerName: 'Deletions', width: 100, field: 'deletionsCount', },
      { headerName: 'Changed Files', width: 120, field: 'changedFilesCount', },
      ...perYearColDefs,
    ];
  }
}
