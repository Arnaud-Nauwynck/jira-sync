import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { GitHubPullRequestsService } from '../../rest/api/gitHubPullRequests.service';
import { GitHubPullRequestDTO } from '../../rest/model/gitHubPullRequestDTO';

@Injectable({ providedIn: 'root' })
export class GithubPullRequestsDataService {

  // Row Data: the last fetched pull requests, shared with anyone injecting this service.
  readonly pullRequests = signal<GitHubPullRequestDTO[]>([]);

  constructor(private gitHubPullRequestsService: GitHubPullRequestsService) {}

  /** Finds a pull request by number, from the currently cached pull requests if present, otherwise from the server. */
  findByNumber(number: number): Observable<GitHubPullRequestDTO> {
    const cached = this.pullRequests().find((pr) => pr.number === number);
    if (cached) {
      return of(cached);
    }
    return this.gitHubPullRequestsService.findPullRequestByNumber(number);
  }

  search(fromYear: number, toYear: number, usernamePattern?: string,
      fromPullRequestNumber?: number | null, toPullRequestNumber?: number | null, pullRequestNumberPattern?: string) {
    this.gitHubPullRequestsService.queryPullRequests(fromYear, toYear, usernamePattern || undefined,
        fromPullRequestNumber ?? undefined, toPullRequestNumber ?? undefined, pullRequestNumberPattern || undefined,
        'body', false, { httpHeaderAccept: 'application/json' as any })
      .subscribe({
        next: (pullRequests) => {
          this.pullRequests.set(pullRequests);
        },
        error: (err) => {
          console.error('failed to load github pull requests', err)
        },
      });
  }
}
