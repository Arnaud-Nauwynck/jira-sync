import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { switchMap } from 'rxjs';
import { AnnotatedJiraIssueDTO } from '../rest/model/annotatedJiraIssueDTO';
import { IssuesDataService } from '../issues-list/issues-data.service';
import { IssueView } from '../issue-view/issue-view';

@Component({
  imports: [IssueView],
  selector: 'app-issue-details',
  templateUrl: './issue-details.html',
})
export class IssueDetails implements OnInit {

  readonly issue = signal<AnnotatedJiraIssueDTO | undefined>(undefined);
  readonly notFound = signal(false);

  constructor(
    private route: ActivatedRoute,
    private issuesDataService: IssuesDataService,
  ) {}

  ngOnInit() {
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
