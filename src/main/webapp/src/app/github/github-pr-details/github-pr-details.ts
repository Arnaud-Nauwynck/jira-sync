import { Component, Input, OnChanges, OnInit, SimpleChanges, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { switchMap } from 'rxjs';
import { GitHubPullRequestDTO } from '../../rest';
import { GithubPullRequestsDataService } from '../github-pr-list/github-pull-requests-data.service';
import { GithubPrView } from '../github-pr-view/github-pr-view';

@Component({
  imports: [GithubPrView],
  selector: 'app-github-pr-details',
  templateUrl: './github-pr-details.html',
})
export class GithubPrDetails implements OnInit, OnChanges {

  /** Pull request number to load when embedded directly (e.g. in a master-detail panel); takes precedence over the route param. */
  @Input() pullRequestNumber?: number;

  readonly pullRequest = signal<GitHubPullRequestDTO | undefined>(undefined);
  readonly notFound = signal(false);

  constructor(
    private route: ActivatedRoute,
    private pullRequestsDataService: GithubPullRequestsDataService,
  ) {}

  ngOnInit() {
    if (this.pullRequestNumber != null) {
      this.loadPullRequest(this.pullRequestNumber);
    } else {
      this.route.paramMap
        .pipe(switchMap((params) => this.pullRequestsDataService.findByNumber(Number(params.get('number')))))
        .subscribe({
          next: (pullRequest) => {
            this.pullRequest.set(pullRequest);
            this.notFound.set(false);
          },
          error: (err) => {
            console.error('failed to load pull request', err);
            this.pullRequest.set(undefined);
            this.notFound.set(true);
          },
        });
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['pullRequestNumber'] && !changes['pullRequestNumber'].firstChange && this.pullRequestNumber != null) {
      this.loadPullRequest(this.pullRequestNumber);
    }
  }

  private loadPullRequest(number: number) {
    this.pullRequestsDataService.findByNumber(number).subscribe({
      next: (pullRequest) => {
        this.pullRequest.set(pullRequest);
        this.notFound.set(false);
      },
      error: (err) => {
        console.error('failed to load pull request', err);
        this.pullRequest.set(undefined);
        this.notFound.set(true);
      },
    });
  }
}
