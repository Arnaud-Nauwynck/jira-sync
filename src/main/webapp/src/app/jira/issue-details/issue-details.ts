import { Component, Input, OnChanges, OnInit, SimpleChanges, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { switchMap } from 'rxjs';
import { JiraIssueDTO } from '../../rest';
import { IssuesDataService } from '../issues-list/issues-data.service';
import { IssueView } from '../issue-view/issue-view';

@Component({
  imports: [IssueView],
  selector: 'app-issue-details',
  templateUrl: './issue-details.html',
})
export class IssueDetails implements OnInit, OnChanges {

  /** Issue key to load when embedded directly (e.g. in a master-detail panel); takes precedence over the route param. */
  @Input() issueKey?: string;

  readonly issue = signal<JiraIssueDTO | undefined>(undefined);
  readonly notFound = signal(false);

  constructor(
    private route: ActivatedRoute,
    private issuesDataService: IssuesDataService,
  ) {}

  ngOnInit() {
    if (this.issueKey) {
      this.loadIssue(this.issueKey);
    } else {
      this.route.paramMap
        .pipe(switchMap((params) => this.issuesDataService.findByKey(params.get('key')!)))
        .subscribe({
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
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['issueKey'] && !changes['issueKey'].firstChange && this.issueKey) {
      this.loadIssue(this.issueKey);
    }
  }

  private loadIssue(key: string) {
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
  }
}
