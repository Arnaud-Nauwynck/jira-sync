import { Component, Input, OnChanges, OnInit, SimpleChanges, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { JiraIssueDTO, NearbyJiraIssuesDTO } from '../../rest';
import { IssuesDataService } from '../service/issues-data.service';
import { IssueView } from '../issue-view/issue-view';

@Component({
  imports: [IssueView],
  selector: 'app-issue-details',
  templateUrl: './issue-details.html',
})
export class IssueDetailsPage implements OnInit, OnChanges {

  /** Issue key to load when embedded directly (e.g. in a master-detail panel); takes precedence over the route param. */
  @Input() issueKey?: string;

  readonly issue = signal<JiraIssueDTO | undefined>(undefined);
  readonly nearby = signal<NearbyJiraIssuesDTO | undefined>(undefined);
  readonly notFound = signal(false);
  readonly refreshing = signal(false);

  private currentKey?: string;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private issuesDataService: IssuesDataService,
  ) {}

  ngOnInit() {
    if (this.issueKey) {
      this.loadIssue(this.issueKey);
    } else {
      this.route.paramMap.subscribe((params) => {
        const key = params.get('key');
        if (key) {
          this.loadIssue(key);
        }
      });
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['issueKey'] && !changes['issueKey'].firstChange && this.issueKey) {
      this.loadIssue(this.issueKey);
    }
  }

  /** Navigates the "prev"/"next" toolbar buttons to another issue: reloads in place when embedded
   * (driven by the `issueKey` @Input, not the route), otherwise navigates the route. */
  goToIssue(key: string | undefined | null) {
    if (!key) {
      return;
    }
    if (this.issueKey) {
      this.loadIssue(key);
    } else {
      this.router.navigate(['/issue', key]);
    }
  }

  refresh() {
    if (this.currentKey == null) {
      return;
    }
    this.refreshing.set(true);
    this.issuesDataService.refreshByKey(this.currentKey).subscribe({
      next: (issue) => {
        this.refreshing.set(false);
        this.issue.set(issue);
        this.notFound.set(false);
      },
      error: (err) => {
        this.refreshing.set(false);
        console.error('failed to refresh issue', err);
        this.notFound.set(true);
      },
    });
  }

  private loadIssue(key: string) {
    this.currentKey = key;
    this.nearby.set(undefined);
    this.issuesDataService.findByKey(key).subscribe({
      next: (issue) => {
        this.issue.set(issue);
        this.notFound.set(false);
      },
      error: (err) => {
        console.error('failed to load issue', err);
        this.issue.set(undefined);
        this.notFound.set(true);
      },
    });
    this.issuesDataService.findNearbyIssues(key).subscribe({
      next: (nearby) => this.nearby.set(nearby),
      error: (err) => console.error('failed to load nearby issues', err),
    });
  }
}
