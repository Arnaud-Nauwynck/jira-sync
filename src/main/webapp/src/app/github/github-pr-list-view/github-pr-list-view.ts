import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { GitHubPrCriteriaDTO, GitHubPullRequestDTO } from '../../rest';

/** The ag-grid list of pull requests: column definitions, and the client-side filtering logic driven
 * by the {@link GitHubPrCriteriaDTO} criteria (owned by the sibling GithubPrSearchCriteriaView). */
@Component({
  imports: [AgGridAngular],
  selector: 'app-github-pr-list-view',
  templateUrl: './github-pr-list-view.html',
})
export class GithubPrListView {

  @Input() rowData: GitHubPullRequestDTO[] = [];
  @Input() criteria: GitHubPrCriteriaDTO = {};
  @Output() readonly rowSelected = new EventEmitter<GitHubPullRequestDTO>();

  // Column Definitions: Defines the columns to be displayed.
  colDefs: ColDef<GitHubPullRequestDTO>[] = [
    { headerName: 'Number', field: 'number', width: 100,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      onCellClicked: (params: CellClickedEvent<GitHubPullRequestDTO>) => {
        if (params.data) {
          this.rowSelected.emit(params.data);
        }
      },
    },
    { headerName: 'State', field: 'state', width: 90 },
    { headerName: 'Draft', field: 'draft', width: 80, cellDataType: 'boolean' },
    { headerName: 'Merged', field: 'merged', width: 90, cellDataType: 'boolean' },
    { headerName: 'Title', field: 'title', width: 400 },
    { headerName: 'Body', field: 'body', width: 250, hide: true },
    { headerName: 'Author', field: 'authorLogin', width: 120 },
    { headerName: 'Assignees', width: 150,
      valueGetter: (params) => (params.data?.assigneeLogins ?? []).join(', '),
    },
    { headerName: 'Requested Reviewers', width: 160,
      valueGetter: (params) => (params.data?.requestedReviewerLogins ?? []).join(', '),
    },
    { headerName: 'Labels', width: 180,
      valueGetter: (params) => (params.data?.labelNames ?? []).join(', '),
    },
    { headerName: 'Milestone', field: 'milestoneTitle', width: 120 },
    { headerName: 'Created', field: 'createdAt', width: 100 },
    { headerName: 'Updated', field: 'updatedAt', width: 100 },
    { headerName: 'Closed', field: 'closedAt', width: 100, },
    { headerName: 'Merged At', field: 'mergedAt', width: 100, },
    { headerName: 'Head Ref', field: 'headRef', width: 140 },
    { headerName: 'Base Ref', field: 'baseRef', width: 120 },
    { headerName: 'Mergeable', field: 'mergeable', width: 100, cellDataType: 'boolean' },
    { headerName: 'Mergeable State', field: 'mergeableState', width: 130 },
    { headerName: 'Merged By', field: 'mergedByLogin', width: 120 },
    { headerName: 'Comments', field: 'comments', width: 100 },

    { headerName: 'Review Comments', field: 'reviewComments', width: 130, },
    { headerName: 'Review Comments Count', width: 100,
      hide: false, // FOR DEBUG
      valueGetter: (params) => (params.data?.reviewCommentsData ?? []).length,
    },
    { headerName: 'Diff Review Comments Count-List', width: 130,
      hide: false, // FOR DEBUG
      valueGetter: (params) => {
        const expected = params.data?.comments || 0;
        const fetched = (params.data?.reviewCommentsData ?? []).length;
        const diff = expected - fetched;
        return (diff)? diff : '';
      },
    },

    { headerName: 'Commits', field: 'commits', width: 90, },
    { headerName: 'Additions', field: 'additions', width: 100, },
    { headerName: 'Deletions', field: 'deletions', width: 100, },
    { headerName: 'Changed Files', field: 'changedFiles', width: 110, },
  ];

  private gridApi?: GridApi<GitHubPullRequestDTO>;

  onGridReady(event: GridReadyEvent<GitHubPullRequestDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  isExternalFilterPresent = (): boolean => {
    const c = this.criteria;
    return c.fromYear != null
      || c.toYear != null
      || (c.usernamePattern ?? '').trim().length > 0
      || c.fromPullRequestNumber != null
      || c.toPullRequestNumber != null
      || (c.pullRequestNumberPattern ?? '').trim().length > 0
      || this.parseCsvList(c.titleContains).length > 0
      || this.parseCsvList(c.bodyContains).length > 0
      || this.parseCsvList(c.authorContains).length > 0
      || this.parseCsvList(c.labelContains).length > 0
      || this.parseCsvList(c.baseRefContains).length > 0
      || this.parseCsvList(c.excludedStates).length > 0
      || (c.draftAvailability ?? 'any') !== 'any'
      || (c.mergedAvailability ?? 'any') !== 'any'
      || (c.mergeableAvailability ?? 'any') !== 'any'
      || (c.mergeableStatePattern ?? '').trim().length > 0
      || this.parseCsvList(c.analysisSummaryContains).length > 0
      || (c.analysisSummaryUpdatedFrom ?? '').length > 0
      || (c.analysisSummaryUpdatedTo ?? '').length > 0
      || c.analysisSummaryMinTokensK != null
      || c.analysisSummaryMaxTokensK != null
      || this.parseCsvList(c.analysisUserExtraPromptsContains).length > 0
      || (c.analysisAvailability ?? 'any') !== 'any'
      || this.parseCsvList(c.developmentWorkDescribedContains).length > 0
      || (c.developmentWorkUpdatedFrom ?? '').length > 0
      || (c.developmentWorkUpdatedTo ?? '').length > 0
      || c.developmentWorkMinTokensK != null
      || c.developmentWorkMaxTokensK != null
      || this.parseCsvList(c.developmentWorkUserExtraPromptsContains).length > 0
      || (c.developmentWorkAvailability ?? 'any') !== 'any'
      || this.parseCsvList(c.personalInterrestCommentContains).length > 0
      || c.personalInterrestMinPriority != null
      || c.personalInterrestMaxPriority != null
      || (c.personalInterrestAvailability ?? 'any') !== 'any';
  };

  doesExternalFilterPass = (node: IRowNode<GitHubPullRequestDTO>): boolean => {
    const pr = node.data;
    const c = this.criteria;
    if (!pr) {
      return true;
    }
    if (!this.matchesYearRange(pr.createdAt)) {
      return false;
    }
    if (!this.matchesUsernamePattern(c.usernamePattern, pr.authorLogin)) {
      return false;
    }
    if (!this.matchesPullRequestNumberPattern(c.pullRequestNumberPattern, pr.number)) {
      return false;
    }
    if (!this.matchesPullRequestNumberRange(c.fromPullRequestNumber, c.toPullRequestNumber, pr.number)) {
      return false;
    }
    if (!this.matchesAny(c.titleContains, pr.title)) {
      return false;
    }
    if (!this.matchesAny(c.bodyContains, pr.body)) {
      return false;
    }
    if (!this.matchesAny(c.authorContains, pr.authorLogin, ...(pr.assigneeLogins ?? []), ...(pr.requestedReviewerLogins ?? []))) {
      return false;
    }
    if (!this.matchesAny(c.labelContains, ...(pr.labelNames ?? []))) {
      return false;
    }
    if (!this.matchesAny(c.baseRefContains, pr.baseRef)) {
      return false;
    }
    if (pr.state != null && this.isExcluded(c.excludedStates, pr.state)) {
      return false;
    }
    if (!this.matchesAvailability(c.draftAvailability, !!pr.draft)) {
      return false;
    }
    if (!this.matchesAvailability(c.mergedAvailability, !!pr.merged)) {
      return false;
    }
    if (!this.matchesAvailability(c.mergeableAvailability, !!pr.mergeable)) {
      return false;
    }
    if (!this.matchesRegex(c.mergeableStatePattern, pr.mergeableState)) {
      return false;
    }
    const annotated = pr.annotated;
    if (!this.matchesAvailability(c.analysisAvailability, !!annotated?.analysisSummary)) {
      return false;
    }
    if (!this.matchesAny(c.analysisSummaryContains, annotated?.analysisSummary)) {
      return false;
    }
    if (!this.matchesDateRange(c.analysisSummaryUpdatedFrom, c.analysisSummaryUpdatedTo, annotated?.analysisSummaryLastUpdateTime)) {
      return false;
    }
    if (!this.matchesTokensRangeK(c.analysisSummaryMinTokensK, c.analysisSummaryMaxTokensK, annotated?.analysisSummaryTokensConsumed)) {
      return false;
    }
    if (!this.matchesAny(c.analysisUserExtraPromptsContains, ...(annotated?.analysisUserExtraPrompts ?? []))) {
      return false;
    }
    if (!this.matchesAvailability(c.developmentWorkAvailability, !!annotated?.developmentWorkDescribed)) {
      return false;
    }
    if (!this.matchesAny(c.developmentWorkDescribedContains, annotated?.developmentWorkDescribed)) {
      return false;
    }
    if (!this.matchesDateRange(c.developmentWorkUpdatedFrom, c.developmentWorkUpdatedTo, annotated?.developmentWorkLastUpdateTime)) {
      return false;
    }
    if (!this.matchesTokensRangeK(c.developmentWorkMinTokensK, c.developmentWorkMaxTokensK, annotated?.developmentWorkTokensConsumed)) {
      return false;
    }
    if (!this.matchesAny(c.developmentWorkUserExtraPromptsContains, ...(annotated?.developmentWorkUserExtraPrompts ?? []))) {
      return false;
    }
    if (!this.matchesAvailability(c.personalInterrestAvailability, !!annotated?.personalInterrestComment)) {
      return false;
    }
    if (!this.matchesAny(c.personalInterrestCommentContains, annotated?.personalInterrestComment)) {
      return false;
    }
    if (!this.matchesNumberRange(c.personalInterrestMinPriority, c.personalInterrestMaxPriority, annotated?.personalInterrestPriority10)) {
      return false;
    }
    return true;
  };

  private matchesAvailability(filter: string | undefined, present: boolean): boolean {
    if (filter === 'yes') {
      return present;
    }
    if (filter === 'no') {
      return !present;
    }
    return true;
  }

  /** Whether the createdAt date's year falls within [fromYear, toYear] (inclusive); unparsable/missing dates pass through. */
  private matchesYearRange(createdAt: string | undefined): boolean {
    if (createdAt == null) {
      return true;
    }
    const year = parseInt(createdAt.substring(0, 4), 10);
    if (isNaN(year)) {
      return true;
    }
    const fromYear = this.criteria.fromYear;
    const toYear = this.criteria.toYear;
    return (fromYear == null || year >= fromYear) && (toYear == null || year <= toYear);
  }

  /** Matches the given regex (full match) against the author login, falling back to "unknown", mirroring the server-side filter. */
  private matchesUsernamePattern(patternText: string | undefined, authorLogin: string | undefined): boolean {
    const name = (authorLogin && authorLogin.trim()) ? authorLogin : 'unknown';
    return this.matchesRegex(patternText, name);
  }

  private matchesPullRequestNumberPattern(patternText: string | undefined, number: number | undefined): boolean {
    return this.matchesRegex(patternText, number != null ? String(number) : undefined);
  }

  /** Whether the PR number falls within [fromNumber, toNumber] (inclusive, either bound optional). */
  private matchesPullRequestNumberRange(fromNumber: number | undefined, toNumber: number | undefined, number: number | undefined): boolean {
    if (fromNumber == null && toNumber == null) {
      return true;
    }
    if (number == null) {
      return false;
    }
    if (fromNumber != null && number < fromNumber) {
      return false;
    }
    if (toNumber != null && number > toNumber) {
      return false;
    }
    return true;
  }

  /** Full-match regex test; a blank pattern always matches, and an invalid regex is treated as no filter. */
  private matchesRegex(patternText: string | undefined, value: string | undefined): boolean {
    const text = (patternText ?? '').trim();
    if (!text) {
      return true;
    }
    if (value == null) {
      return false;
    }
    try {
      return new RegExp(`^(?:${text})$`).test(value);
    } catch {
      return true;
    }
  }

  private matchesAny(filterValue: string | undefined, ...values: (string | undefined)[]): boolean {
    const terms = this.parseCsvList(filterValue);
    if (terms.length === 0) {
      return true;
    }
    return values.some((value) =>
      value != null && terms.some((term) => value.toLowerCase().includes(term.toLowerCase())));
  }

  /** Whether the value is in the comma-separated excluded list. */
  private isExcluded(excludedCsv: string | undefined, value: string): boolean {
    return this.parseCsvList(excludedCsv).includes(value);
  }

  private matchesDateRange(from: string | undefined, to: string | undefined, value: string | undefined): boolean {
    if (!from && !to) {
      return true;
    }
    if (value == null) {
      return false;
    }
    if (from && value < from) {
      return false;
    }
    if (to && value > `${to}T23:59:59`) {
      return false;
    }
    return true;
  }

  private matchesNumberRange(min: number | undefined, max: number | undefined, value: number | undefined): boolean {
    if (min == null && max == null) {
      return true;
    }
    if (value == null) {
      return false;
    }
    if (min != null && value < min) {
      return false;
    }
    if (max != null && value > max) {
      return false;
    }
    return true;
  }

  /** min/max are expressed in kilo-tokens (thousands); value is the raw token count. */
  private matchesTokensRangeK(minK: number | undefined, maxK: number | undefined, value: number | undefined): boolean {
    if (minK == null && maxK == null) {
      return true;
    }
    if (value == null) {
      return false;
    }
    if (minK != null && value < minK * 1000) {
      return false;
    }
    if (maxK != null && value > maxK * 1000) {
      return false;
    }
    return true;
  }

  private parseCsvList(value: string | undefined): string[] {
    return (value ?? '')
      .split(',')
      .map((term) => term.trim())
      .filter((term) => term.length > 0);
  }

}
