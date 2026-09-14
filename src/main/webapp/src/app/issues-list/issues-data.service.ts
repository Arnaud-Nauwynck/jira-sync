import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { JiraIssuesService } from '../rest/api/jiraIssues.service';
import { JiraIssueDTO } from '../rest/model/jiraIssueDTO';
import { AvailabilityFilter } from './filters/availability-filter';

/** Mirrors the backend's JiraIssueQueryCriteriaDTO: the Main/Analysis/Development Work/Personal Interest
 * filter criteria of the issues-list page, sent to the server so the initial fetch is already narrowed down. */
export interface IssuesSearchCriteria {
  fromYear: number;
  toYear: number;
  usernamePattern?: string;
  fromNumber?: number | null;
  toNumber?: number | null;
  keyPattern?: string;

  summaryContains?: string;
  descriptionContains?: string;
  authorContains?: string;
  commentsContains?: string;
  commentAuthorContains?: string;
  excludedTypes?: Set<string>;
  excludedResolutions?: Set<string>;
  excludedStatuses?: Set<string>;
  excludedPriorities?: Set<string>;
  labelsContains?: string;
  pullRequestAvailableLabel?: AvailabilityFilter;

  analysisSummaryContains?: string;
  analysisUserExtraPromptsContains?: string;
  analysisSummaryUpdatedFrom?: string;
  analysisSummaryUpdatedTo?: string;
  analysisSummaryMinTokensK?: number | null;
  analysisSummaryMaxTokensK?: number | null;
  analysisAvailability?: AvailabilityFilter;

  developmentWorkDescribedContains?: string;
  developmentWorkUserExtraPromptsContains?: string;
  developmentWorkUpdatedFrom?: string;
  developmentWorkUpdatedTo?: string;
  developmentWorkMinTokensK?: number | null;
  developmentWorkMaxTokensK?: number | null;
  developmentWorkAvailability?: AvailabilityFilter;

  personalInterrestCommentContains?: string;
  personalInterrestMinPriority?: number | null;
  personalInterrestMaxPriority?: number | null;
  personalInterrestAvailability?: AvailabilityFilter;
}

@Injectable({ providedIn: 'root' })
export class IssuesDataService {

  // Row Data: the last fetched issues, shared with anyone injecting this service.
  readonly issues = signal<JiraIssueDTO[]>([]);

  constructor(private jiraIssuesService: JiraIssuesService) {}

  search(criteria: IssuesSearchCriteria) {
    this.jiraIssuesService.queryAnnotatedIssues(
        criteria.fromYear, criteria.toYear, criteria.usernamePattern || undefined,
        criteria.fromNumber ?? undefined, criteria.toNumber ?? undefined, criteria.keyPattern || undefined,
        criteria.summaryContains || undefined, criteria.descriptionContains || undefined,
        criteria.authorContains || undefined, criteria.commentsContains || undefined,
        criteria.commentAuthorContains || undefined,
        joinCsv(criteria.excludedTypes), joinCsv(criteria.excludedResolutions),
        joinCsv(criteria.excludedStatuses), joinCsv(criteria.excludedPriorities),
        criteria.labelsContains || undefined, availabilityOrUndefined(criteria.pullRequestAvailableLabel),
        criteria.analysisSummaryContains || undefined, criteria.analysisUserExtraPromptsContains || undefined,
        criteria.analysisSummaryUpdatedFrom || undefined, criteria.analysisSummaryUpdatedTo || undefined,
        criteria.analysisSummaryMinTokensK ?? undefined, criteria.analysisSummaryMaxTokensK ?? undefined,
        availabilityOrUndefined(criteria.analysisAvailability),
        criteria.developmentWorkDescribedContains || undefined, criteria.developmentWorkUserExtraPromptsContains || undefined,
        criteria.developmentWorkUpdatedFrom || undefined, criteria.developmentWorkUpdatedTo || undefined,
        criteria.developmentWorkMinTokensK ?? undefined, criteria.developmentWorkMaxTokensK ?? undefined,
        availabilityOrUndefined(criteria.developmentWorkAvailability),
        criteria.personalInterrestCommentContains || undefined,
        criteria.personalInterrestMinPriority ?? undefined, criteria.personalInterrestMaxPriority ?? undefined,
        availabilityOrUndefined(criteria.personalInterrestAvailability),
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
  findByKey(key: string): Observable<JiraIssueDTO> {
    const cached = this.issues().find((issue) => issue.key === key);
    if (cached) {
      return of(cached);
    }
    return this.jiraIssuesService.findAnnotatedIssueByKey(key);
  }
}

function joinCsv(values: Set<string> | undefined): string | undefined {
  return values && values.size > 0 ? Array.from(values).join(',') : undefined;
}

function availabilityOrUndefined(value: AvailabilityFilter | undefined): string | undefined {
  return value && value !== 'any' ? value : undefined;
}
