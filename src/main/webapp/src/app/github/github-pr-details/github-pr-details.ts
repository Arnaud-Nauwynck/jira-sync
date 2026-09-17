import { Component, Input, OnChanges, OnInit, SimpleChanges, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { switchMap } from 'rxjs';
import { GitHubPullRequestDTO } from '../../rest';
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
  readonly notFound = signal(false);
  readonly refreshing = signal(false);

  private currentNumber?: number;

  constructor(
    private route: ActivatedRoute,
    private pullRequestsDataService: GithubPullRequestsDataService,
  ) {}

  ngOnInit() {
    if (this.pullRequestNumber != null) {
      this.loadPullRequest(this.pullRequestNumber);
    } else {
      this.route.paramMap
        .pipe(switchMap((params) => {
          const number = Number(params.get('number'));
          this.currentNumber = number;
          return this.pullRequestsDataService.findByNumber(number);
        }))
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
