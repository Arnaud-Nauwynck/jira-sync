import { Component, OnInit, ViewChild, signal } from '@angular/core';
import { Router } from '@angular/router';
import { GitHubPullRequestDTO } from '../../rest';
import { GithubPullRequestsDataService } from '../service/github-pull-requests-data.service';
import { GithubPrView } from '../github-pr-view/github-pr-view';
import { GithubPrListView } from '../github-pr-list-view/github-pr-list-view';
import { GithubPrSearchCriteriaView } from '../github-pr-search-criteria-view/github-pr-search-criteria-view';
import { SearchStatsBar } from '../../jira/issues-search-page/filters/search-stats-bar';

@Component({
  imports: [GithubPrView, GithubPrListView, GithubPrSearchCriteriaView, SearchStatsBar],
  selector: 'app-github-pr-list',
  templateUrl: './github-pr-search-page.html',
})
export class GithubPrSearchPage implements OnInit {

  @ViewChild(GithubPrSearchCriteriaView, { static: true }) criteriaView!: GithubPrSearchCriteriaView;
  @ViewChild(GithubPrListView, { static: true }) listView!: GithubPrListView;

  // The pull request currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedPullRequest = signal<GitHubPullRequestDTO | undefined>(undefined);

  // Cap passed to the server on a normal search.
  limit = 5000;
  // Higher cap used instead of `limit` when the currently selected [fromYear, toYear] range's full
  // partition data still fits under it: the whole range is then fetched and cached client-side, so
  // further Main/Analysis/Development Work/Personal Interest criteria tweaks never need a re-fetch.
  cachedLimit = 10000;

  constructor(readonly pullRequestsDataService: GithubPullRequestsDataService, private router: Router) {}

  ngOnInit() {
    this.search();
    this.pullRequestsDataService.loadPartitionStats();
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

  search() {
    this.pullRequestsDataService.query(this.criteriaView.criteria, this.effectiveLimit());
  }

  /** Uses `cachedLimit` instead of `limit` when the [fromYear, toYear] range's full partition data
   * (per the partition stats) still fits under it, so the fetch captures the whole range. */
  private effectiveLimit(): number {
    const available = this.availableInRange();
    return available > 0 && available <= this.cachedLimit ? this.cachedLimit : this.limit;
  }

  /** Sum of the "created_year" partition counts falling within the current [fromYear, toYear] criteria. */
  availableInRange(): number {
    const stats = this.pullRequestsDataService.partitionStats()?.statsPerYear ?? [];
    const fromYear = this.criteriaView?.criteria.fromYear;
    const toYear = this.criteriaView?.criteria.toYear;
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
