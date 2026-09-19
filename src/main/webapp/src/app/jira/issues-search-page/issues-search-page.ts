import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { IssuesCriteriaDTO, JiraIssueDTO } from '../../rest';
import { IssuesDataService } from '../service/issues-data.service';
import { IssueView } from '../issue-view/issue-view';
import { IssuesListView } from '../issues-list-view/issues-list-view';
import { IssuesListCriteriaView } from '../issues-list-criteria-view/issues-list-criteria-view';
import { SearchStatsBar } from './filters/search-stats-bar';
import { applyQueryParamsToCriteria, criteriaToQueryParams } from '../../utils/criteria-query-params';
import { httpErrorMessage } from '../../utils/http-error-message';

/** Criteria fields to convert back from string to number when read from the URL query params. */
const NUMERIC_CRITERIA_KEYS = ['fromYear', 'toYear', 'fromNumber', 'toNumber',
  'analysisSummaryMinTokensK', 'analysisSummaryMaxTokensK',
  'developmentWorkMinTokensK', 'developmentWorkMaxTokensK',
  'personalInterrestMinPriority', 'personalInterrestMaxPriority'];

@Component({
  imports: [IssueView, IssuesListView, IssuesListCriteriaView, SearchStatsBar],
  selector: 'app-issues-list',
  templateUrl: './issues-search-page.html',
})
export class IssuesSearchPage implements OnInit {

  /** Search criteria, edited in-place by the criteria view, and sent as-is to the server on a search.
   * Held by the root-scoped data service, so it survives leaving and re-entering this page. */
  get criteria(): IssuesCriteriaDTO {
    return this.issuesDataService.criteria;
  }

  /** Cap passed to the server on a search: held by the data service, alongside the criteria. */
  get limit(): number {
    return this.issuesDataService.limit;
  }
  set limit(limit: number) {
    this.issuesDataService.limit = limit;
  }

  // The issue currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedIssue = signal<JiraIssueDTO | undefined>(undefined);

  // True while a server search is in-flight, to disable the "Search" button.
  loading = false;

  // Error of the last failed search, displayed next to the "Search" button, or '' when the last search succeeded.
  loadErrorMessage = '';

  private readonly destroyRef = inject(DestroyRef);

  constructor(
    readonly issuesDataService: IssuesDataService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    const limitParam = applyQueryParamsToCriteria(this.criteria, this.route.snapshot.queryParams, NUMERIC_CRITERIA_KEYS);
    if (limitParam) {
      this.limit = limitParam;
    }
    // Re-use the rows already cached by the data service when they match: re-entering the page is then instant.
    if (!this.issuesDataService.isUpToDate(this.criteria, this.limit)) {
      this.search();
    }
    this.issuesDataService.loadPartitionStats();
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

  search() {
    this.loading = true;
    this.loadErrorMessage = '';
    this.publishCriteriaAsQueryParams();
    this.issuesDataService.query(this.criteria, this.limit)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.loading = false;
        },
        error: (err) => {
          console.error('failed to search issues', err)
          this.loadErrorMessage = httpErrorMessage(err);
          this.loading = false;
        },
      });
  }

  /** Mirrors the searched criteria into the URL, so the search is bookmarkable and survives a reload. */
  private publishCriteriaAsQueryParams() {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: criteriaToQueryParams(this.criteria, this.limit),
      replaceUrl: true,
    });
  }

  /** Sum of the "created_year" partition counts falling within the current [fromYear, toYear] criteria. */
  availableInRange(): number {
    const stats = this.issuesDataService.partitionStats()?.statsPerYear ?? [];
    const fromYear = this.criteria.fromYear;
    const toYear = this.criteria.toYear;
    return stats
      .filter((s) => (fromYear == null || (s.year ?? 0) >= fromYear) && (toYear == null || (s.year ?? 0) <= toYear))
      .reduce((sum, s) => sum + (s.count ?? 0), 0);
  }

  /** Grand total of issues across every "created_year" partition. */
  grandTotal(): number {
    const stats = this.issuesDataService.partitionStats()?.statsPerYear ?? [];
    return stats.reduce((sum, s) => sum + (s.count ?? 0), 0);
  }

}
