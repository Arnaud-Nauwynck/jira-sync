import { Component, DestroyRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { GitHubPrCriteriaDTO, GitHubPullRequestDTO } from '../../rest';
import { GithubPullRequestsDataService } from '../service/github-pull-requests-data.service';
import { GithubPrView } from '../github-pr-view/github-pr-view';
import { GithubPrListView } from '../github-pr-list-view/github-pr-list-view';
import { GithubPrSearchCriteriaView, GithubPrSearchMode } from '../github-pr-search-criteria-view/github-pr-search-criteria-view';
import { SearchStatsBar } from '../../jira/issues-search-page/filters/search-stats-bar';
import { applyQueryParamsToCriteria, criteriaToQueryParams } from '../../utils/criteria-query-params';
import { httpErrorMessage } from '../../utils/http-error-message';

/** Criteria fields to convert back from string to number when read from the URL query params. */
const NUMERIC_CRITERIA_KEYS = ['fromYear', 'toYear', 'fromPullRequestNumber', 'toPullRequestNumber',
  'analysisSummaryMinTokensK', 'analysisSummaryMaxTokensK',
  'developmentWorkMinTokensK', 'developmentWorkMaxTokensK',
  'personalInterrestMinPriority', 'personalInterrestMaxPriority'];

@Component({
  imports: [GithubPrView, GithubPrListView, GithubPrSearchCriteriaView, SearchStatsBar],
  selector: 'app-github-pr-list',
  templateUrl: './github-pr-search-page.html',
})
export class GithubPrSearchPage implements OnInit {

  /** Search criteria, edited in-place by the criteria view, and sent as-is to the server on a search.
   * Held by the root-scoped data service, so it survives leaving and re-entering this page. */
  get criteria(): GitHubPrCriteriaDTO {
    return this.pullRequestsDataService.criteria;
  }

  /** Cap passed to the server on a search: held by the data service, alongside the criteria. */
  get limit(): number {
    return this.pullRequestsDataService.limit;
  }
  set limit(limit: number) {
    this.pullRequestsDataService.limit = limit;
  }

  // The pull request currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedPullRequest = signal<GitHubPullRequestDTO | undefined>(undefined);

  // The grid, absent while hidden (see hideList): queried by type since a template reference variable
  // declared inside an @if block isn't visible to bindings outside of that block.
  private readonly listView = viewChild(GithubPrListView);

  // Mode of the criteria view's shrinked panel, mirrored here to know when a "by id" search should
  // auto-open its unique match (see search()).
  searchMode: GithubPrSearchMode = 'recent';

  // True once a "by id" search auto-opened its unique match: the grid is then hidden, as if the row
  // had been picked from it. Reset by closeDetail() or by leaving "by id" mode.
  readonly hideList = signal(false);

  // True while a server search is in-flight, to disable the "Search" button.
  loading = false;

  // Error of the last failed search, displayed next to the "Search" button, or '' when the last search succeeded.
  loadErrorMessage = '';

  private readonly destroyRef = inject(DestroyRef);

  constructor(
    readonly pullRequestsDataService: GithubPullRequestsDataService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    const limitParam = applyQueryParamsToCriteria(this.criteria, this.route.snapshot.queryParams, NUMERIC_CRITERIA_KEYS);
    if (limitParam) {
      this.limit = limitParam;
    }
    // Re-use the rows already cached by the data service when they match: re-entering the page is then instant.
    if (!this.pullRequestsDataService.isUpToDate(this.criteria, this.limit)) {
      this.search();
    }
    this.pullRequestsDataService.loadPartitionStats();
  }

  closeDetail() {
    this.selectedPullRequest.set(undefined);
    this.hideList.set(false);
  }

  onSearchModeChange(mode: GithubPrSearchMode) {
    this.searchMode = mode;
    if (mode !== 'byId') {
      this.hideList.set(false);
    }
  }

  onCriteriaChanged() {
    const listView = this.listView();
    listView?.onFilterInputsChanged();
    // The grid's client-side filter reflects the in-place criteria edit immediately; `pullRequestsDataService.pullRequests()`
    // does not, since it still holds the last *server* search's rows until a new search is issued.
    this.applyByIdAutoSelect(listView?.getDisplayedRows() ?? []);
  }

  openDetailAsRoute() {
    const number = this.selectedPullRequest()?.number;
    if (number != null) {
      this.router.navigate(['/github-pull-request', number]);
    }
  }

  search() {
    this.loading = true;
    this.loadErrorMessage = '';
    this.publishCriteriaAsQueryParams();
    this.pullRequestsDataService.query(this.criteria, this.limit)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.loading = false;
          this.applyByIdAutoSelect(this.pullRequestsDataService.pullRequests());
        },
        error: (err) => {
          console.error('failed to search github pull requests', err)
          this.loadErrorMessage = httpErrorMessage(err);
          this.loading = false;
        },
      });
  }

  /** In "by id" mode, opens the detail panel and hides the grid as soon as `results` narrows down to a
   * single PR, as if that row had been picked from the grid; otherwise leaves the grid showing. */
  private applyByIdAutoSelect(results: GitHubPullRequestDTO[]) {
    if (this.searchMode !== 'byId') {
      return;
    }
    if (results.length === 1) {
      this.selectedPullRequest.set(results[0]);
      this.hideList.set(true);
    } else {
      this.selectedPullRequest.set(undefined);
      this.hideList.set(false);
    }
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
    const stats = this.pullRequestsDataService.partitionStats()?.statsPerYear ?? [];
    const fromYear = this.criteria.fromYear;
    const toYear = this.criteria.toYear;
    return stats
      .filter((s) => (fromYear == null || (s.year ?? 0) >= fromYear) && (toYear == null || (s.year ?? 0) <= toYear))
      .reduce((sum, s) => sum + (s.count ?? 0), 0);
  }

  /** Grand total of PRs across every "created_year" partition. */
  grandTotal(): number {
    const stats = this.pullRequestsDataService.partitionStats()?.statsPerYear ?? [];
    return stats.reduce((sum, s) => sum + (s.count ?? 0), 0);
  }

}
