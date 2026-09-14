import { Injectable, signal } from '@angular/core';
import { GitHubPullRequestsService } from '../rest/api/gitHubPullRequests.service';
import { GitHubPullRequestDTO } from '../rest/model/gitHubPullRequestDTO';

@Injectable({ providedIn: 'root' })
export class GithubPullRequestsDataService {

  // Row Data: the last fetched pull requests, shared with anyone injecting this service.
  readonly pullRequests = signal<GitHubPullRequestDTO[]>([]);

  constructor(private gitHubPullRequestsService: GitHubPullRequestsService) {}

  search(fromYear: number, toYear: number, usernamePattern?: string) {
    this.gitHubPullRequestsService.queryPullRequests(fromYear, toYear, usernamePattern || undefined,
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
