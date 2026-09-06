import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { JiraIssuesService } from '../rest/api/jiraIssues.service';
import { AnnotatedJiraIssueDTO } from '../rest/model/annotatedJiraIssueDTO';

@Injectable({ providedIn: 'root' })
export class IssuesDataService {

  // Row Data: the last fetched issues, shared with anyone injecting this service.
  readonly issues = signal<AnnotatedJiraIssueDTO[]>([]);

  constructor(private jiraIssuesService: JiraIssuesService) {}

  search(fromYear: number, toYear: number, usernamePattern?: string) {
    this.jiraIssuesService.queryAnnotatedIssues(fromYear, toYear, usernamePattern || undefined,
        'body', false, { httpHeaderAccept: 'application/json' as any })
      .subscribe({
        next: (issues) => {
          this.issues.set(issues);
        },
        error: (err) => {
          console.error('failed to load annotated issues', err)
        },
      });
  }

  /** Finds an issue by key, from the currently cached issues if present, otherwise from the server. */
  findByKey(key: string): Observable<AnnotatedJiraIssueDTO> {
    const cached = this.issues().find((issue) => issue.key === key);
    if (cached) {
      return of(cached);
    }
    return this.jiraIssuesService.findAnnotatedIssueByKey(key);
  }
}
