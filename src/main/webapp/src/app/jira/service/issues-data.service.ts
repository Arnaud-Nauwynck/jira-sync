import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { IssuesCriteriaDTO, IssuesPartitionStatsDTO, JiraIssueDTO, JiraIssuesService, NearbyJiraIssuesDTO } from '../../rest';

const DEFAULT_LIMIT = 1000;

@Injectable({ providedIn: 'root' })
export class IssuesDataService {

  // Row Data: the last fetched issues, shared with anyone injecting this service.
  readonly issues = signal<JiraIssueDTO[]>([]);

  // The criteria (and limit) that produced the current `issues` result list.
  readonly lastCriteria = signal<IssuesCriteriaDTO | undefined>(undefined);
  readonly lastLimit = signal<number>(DEFAULT_LIMIT);

  // Count of locally-synced issues per "created_year" partition, loaded once and used to show how many
  // issues are available versus how many currently match the search criteria.
  readonly partitionStats = signal<IssuesPartitionStatsDTO | undefined>(undefined);

  constructor(private jiraIssuesService: JiraIssuesService) {}

  query(criteria: IssuesCriteriaDTO, limit = DEFAULT_LIMIT) {
    this.jiraIssuesService.queryIssues({ criteria, limit })
      .subscribe({
        next: (issues) => {
          this.issues.set(issues);
          this.lastCriteria.set(criteria);
          this.lastLimit.set(limit);
        },
        error: (err) => {
          console.error('failed to load annotated issues', err)
        },
      });
  }

  loadPartitionStats() {
    this.jiraIssuesService.queryPartitionStats1()
      .subscribe({
        next: (stats) => {
          this.partitionStats.set(stats);
        },
        error: (err) => {
          console.error('failed to load jira issues partition stats', err)
        },
      });
  }

  /** Finds an issue by key, from the currently cached issues if present, otherwise from the server. */
  findByKey(key: string): Observable<JiraIssueDTO> {
    const cached = this.issues().find((issue) => issue.key === key);
    if (cached) {
      return of(cached);
    }
    return this.jiraIssuesService.findIssueByKey(key);
  }

  /** Finds the keys of the issues nearest to the given one (by issue number, within the same project). */
  findNearbyIssues(key: string): Observable<NearbyJiraIssuesDTO> {
    return this.jiraIssuesService.findNearbyIssues(key);
  }

  /** Re-fetches an issue by key from the server, bypassing the cache, and updates it in the cache if present. */
  refreshByKey(key: string): Observable<JiraIssueDTO> {
    const result = this.jiraIssuesService.findIssueByKey(key);
    result.subscribe((issue) => {
      const issues = this.issues();
      const index = issues.findIndex((it) => it.key === key);
      if (index >= 0) {
        this.issues.set([...issues.slice(0, index), issue, ...issues.slice(index + 1)]);
      }
    });
    return result;
  }
}
