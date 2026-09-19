import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { GitHubPullRequestsService } from '../../rest';
import { GitHubPrCriteriaDTO } from '../../rest';
import { GitHubPrPartitionStatsDTO } from '../../rest';
import { GitHubPullRequestDTO } from '../../rest';
import { NearbyGitHubPullRequestsDTO } from '../../rest';

const DEFAULT_LIMIT = 1000;

@Injectable({ providedIn: 'root' })
export class GithubPullRequestsDataService {

  // Row Data: the last fetched pull requests, shared with anyone injecting this service.
  readonly pullRequests = signal<GitHubPullRequestDTO[]>([]);

  // The criteria (and limit) that produced the current `pullRequests` result list.
  readonly lastCriteria = signal<GitHubPrCriteriaDTO | undefined>(undefined);
  readonly lastLimit = signal<number>(DEFAULT_LIMIT);

  // Count of locally-synced PRs per "created_year" partition, loaded once and used to show how many
  // PRs are available versus how many currently match the search criteria.
  readonly partitionStats = signal<GitHubPrPartitionStatsDTO | undefined>(undefined);

  constructor(private gitHubPullRequestsService: GitHubPullRequestsService) {}

  /** Finds a pull request by number, from the currently cached pull requests if present, otherwise from the server. */
  findByNumber(number: number): Observable<GitHubPullRequestDTO> {
    const cached = this.pullRequests().find((pr) => pr.number === number);
    if (cached) {
      return of(cached);
    }
    return this.gitHubPullRequestsService.findPullRequestByNumber(number);
  }

  /** Finds the numbers of the PRs nearest to the given one (by PR number). */
  findNearbyPullRequests(number: number): Observable<NearbyGitHubPullRequestsDTO> {
    return this.gitHubPullRequestsService.findNearbyPullRequests(number);
  }

  /** Re-fetches a pull request by number from the server, bypassing the cache, and updates it in the cache if present. */
  refreshByNumber(number: number): Observable<GitHubPullRequestDTO> {
    const result = this.gitHubPullRequestsService.findPullRequestByNumber(number);
    result.subscribe((pullRequest) => {
      const pullRequests = this.pullRequests();
      const index = pullRequests.findIndex((pr) => pr.number === number);
      if (index >= 0) {
        this.pullRequests.set([...pullRequests.slice(0, index), pullRequest, ...pullRequests.slice(index + 1)]);
      }
    });
    return result;
  }

  query(criteria: GitHubPrCriteriaDTO, limit = DEFAULT_LIMIT) {
    this.gitHubPullRequestsService.queryPullRequests({ criteria, limit })
      .subscribe({
        next: (pullRequests) => {
          this.pullRequests.set(pullRequests);
          this.lastCriteria.set(criteria);
          this.lastLimit.set(limit);
        },
        error: (err) => {
          console.error('failed to load github pull requests', err)
        },
      });
  }

  loadPartitionStats() {
    this.gitHubPullRequestsService.queryPartitionStats2()
      .subscribe({
        next: (stats) => {
          this.partitionStats.set(stats);
        },
        error: (err) => {
          console.error('failed to load github pr partition stats', err)
        },
      });
  }
}
