import { Component, OnInit, ViewChild, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { JiraIssueDTO } from '../../rest';
import { IssuesDataService } from './issues-data.service';
import { IssueView } from '../issue-view/issue-view';
import { IssuesListView } from '../issues-list-view/issues-list-view';
import { IssuesListCriteriaView } from '../issues-list-criteria-view/issues-list-criteria-view';

@Component({
  imports: [FormsModule, IssueView, IssuesListView, IssuesListCriteriaView],
  selector: 'app-issues-list',
  templateUrl: './issues-search-page.html',
})
export class IssuesSearchPage implements OnInit {

  @ViewChild(IssuesListCriteriaView, { static: true }) criteriaView!: IssuesListCriteriaView;
  @ViewChild(IssuesListView, { static: true }) listView!: IssuesListView;

  // The issue currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedIssue = signal<JiraIssueDTO | undefined>(undefined);

  // Cap passed to the server on a normal search.
  limit = 5000;
  // Higher cap used instead of `limit` when the currently selected [fromYear, toYear] range's full
  // partition data still fits under it: the whole range is then fetched and cached client-side, so
  // further Main/Analysis/Development Work/Personal Interest criteria tweaks never need a re-fetch.
  cachedLimit = 10000;

  constructor(readonly issuesDataService: IssuesDataService, private router: Router) {}

  ngOnInit() {
    this.search();
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
    this.issuesDataService.query(this.criteriaView.criteria, this.effectiveLimit());
  }

  /** Uses `cachedLimit` instead of `limit` when the [fromYear, toYear] range's full partition data
   * (per the partition stats) still fits under it, so the fetch captures the whole range. */
  private effectiveLimit(): number {
    const available = this.availableInRange();
    return available > 0 && available <= this.cachedLimit ? this.cachedLimit : this.limit;
  }

  /** Sum of the "created_year" partition counts falling within the current [fromYear, toYear] criteria. */
  availableInRange(): number {
    const stats = this.issuesDataService.partitionStats()?.statsPerYear ?? [];
    const fromYear = this.criteriaView?.criteria.fromYear;
    const toYear = this.criteriaView?.criteria.toYear;
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
