import { Component, Input, OnChanges, OnInit, SimpleChanges, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GitHubPullRequestDTO, NearbyGitHubPullRequestsDTO } from '../../rest';
import { GithubPullRequestsDataService } from '../service/github-pull-requests-data.service';
import { GithubPrView } from '../github-pr-view/github-pr-view';

@Component({
  imports: [GithubPrView],
  selector: 'app-github-pr-details',
  templateUrl: './github-pr-details.html',
})
export class GithubPrDetailsPage implements OnInit, OnChanges {

  /** Pull request number to load when embedded directly (e.g. in a master-detail panel); takes precedence over the route param. */
  @Input() pullRequestNumber?: number;

  readonly pullRequest = signal<GitHubPullRequestDTO | undefined>(undefined);
  readonly nearby = signal<NearbyGitHubPullRequestsDTO | undefined>(undefined);
  readonly notFound = signal(false);
  readonly refreshing = signal(false);

  private currentNumber?: number;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private pullRequestsDataService: GithubPullRequestsDataService,
  ) {}

  ngOnInit() {
    if (this.pullRequestNumber != null) {
      this.loadPullRequest(this.pullRequestNumber);
    } else {
      this.route.paramMap.subscribe((params) => {
        const number = Number(params.get('number'));
        if (!isNaN(number)) {
          this.loadPullRequest(number);
        }
      });
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['pullRequestNumber'] && !changes['pullRequestNumber'].firstChange && this.pullRequestNumber != null) {
      this.loadPullRequest(this.pullRequestNumber);
    }
  }

  /** Navigates the "prev"/"next" toolbar buttons to another PR: reloads in place when embedded
   * (driven by the `pullRequestNumber` @Input, not the route), otherwise navigates the route. */
  goToPullRequest(number: number | undefined | null) {
    if (number == null) {
      return;
    }
    if (this.pullRequestNumber != null) {
      this.loadPullRequest(number);
    } else {
      this.router.navigate(['/github-pull-request', number]);
    }
  }

  refresh() {
    if (this.currentNumber == null) {
      return;
    }
    this.refreshing.set(true);
    this.pullRequestsDataService.refreshByNumber(this.currentNumber).subscribe({
      next: (pullRequest) => {
        this.refreshing.set(false);
        this.pullRequest.set(pullRequest);
        this.notFound.set(false);
      },
      error: (err) => {
        this.refreshing.set(false);
        console.error('failed to refresh pull request', err);
        this.notFound.set(true);
      },
    });
  }

  private loadPullRequest(number: number) {
    this.currentNumber = number;
    this.nearby.set(undefined);
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
    this.pullRequestsDataService.findNearbyPullRequests(number).subscribe({
      next: (nearby) => this.nearby.set(nearby),
      error: (err) => console.error('failed to load nearby pull requests', err),
    });
  }
}
