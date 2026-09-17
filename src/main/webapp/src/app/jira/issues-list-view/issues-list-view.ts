import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { IssuesCriteriaDTO, JiraIssueDTO } from '../../rest';

const OTHER_RESOLUTIONS = '(others)';
const OTHER_TYPES = '(others)';
const PULL_REQUEST_AVAILABLE_LABEL = 'pull-request-available';

const KNOWN_TYPES = new Set(['Bug', 'Improvement', 'New Feature', 'Story', 'Epic', 'Sub-task', 'Task', 'Umbrella', 'Question',
  'Wish', 'Test', 'Documentation', 'IT Help', 'Brainstorming', 'Dependency upgrade', 'Request',
  'Planned Work', 'Github Integration', 'RTC', 'Blog - New Blog Request']);

const KNOWN_RESOLUTIONS = new Set(['Done', 'Fixed', 'Invalid', 'Incomplete', 'Cannot Reproduce', 'Works for Me', 'Not A Problem',
  "Won't Fix", "Won't Do", 'Later', 'Duplicate', 'Resolved', 'Not A Bug', 'Abandoned', 'Auto Closed',
  'WorkAround', 'Workaround', 'Implemented', 'Information Provided', '']);

/** The ag-grid list of issues: column definitions, and the client-side filtering logic driven by the
 * {@link IssuesCriteriaDTO} criteria (owned by the sibling IssuesListCriteriaView). */
@Component({
  imports: [AgGridAngular],
  selector: 'app-issues-list-view',
  templateUrl: './issues-list-view.html',
})
export class IssuesListView {

  @Input() rowData: JiraIssueDTO[] = [];
  @Input() criteria: IssuesCriteriaDTO = {};
  @Output() readonly rowSelected = new EventEmitter<JiraIssueDTO>();

  // Column Definitions: Defines the columns to be displayed.
  colDefs: ColDef<JiraIssueDTO>[] = [
    { headerName: 'Key', field: 'key', width: 120,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      onCellClicked: (params: CellClickedEvent<JiraIssueDTO>) => {
        if (params.data) {
          this.rowSelected.emit(params.data);
        }
      },
    },
    { headerName: 'Type', field: 'fields.issuetype', width: 105 },
    { headerName: 'Status', field: 'fields.status', width: 80 },
    { headerName: 'Priority', field: 'fields.priority', width: 80  },
    { headerName: 'Resolution', field: 'fields.resolution', width: 95  },
    { headerName: 'Components', width: 150,
      valueGetter: (params) => (params.data?.fields?.components ?? []).join(', '),
    },
    { headerName: 'Labels', width: 150,
      valueGetter: (params) => (params.data?.fields?.labels ?? []).join(', '),
    },
    { headerName: 'PR Available', width: 110, cellDataType: 'boolean',
      valueGetter: (params) => (params.data?.fields?.labels ?? []).includes(PULL_REQUEST_AVAILABLE_LABEL),
    },
    { headerName: 'Summary', field: 'fields.summary', width: 400},
    { headerName: 'Description', field: 'fields.description', width: 200},
    { headerName: 'Project', hide: true, field: 'fields.project', },
    { headerName: 'Creator', field: 'fields.creator', width: 100 },
    { headerName: 'Reporter', field: 'fields.reporter', width: 100 },
    { headerName: 'Assignee', field: 'fields.assignee', width: 100 },
    { headerName: 'Created', field: 'fields.created', width: 100 },
    { headerName: 'Updated', field: 'fields.updated', width: 100 },
    { headerName: 'Votes', width: 70,
      valueGetter: (params) => {
        const votes = params.data?.fields?.votes;
        return (votes)? votes : '';
      },
    },
    { headerName: 'Watches', width: 70,
      valueGetter: (params) => {
        const watchCount = params.data?.fields?.watchCount;
        return (watchCount)? watchCount : '';
      },
    },
    { headerName: 'Annotated', width: 100, cellDataType: 'boolean',
      valueGetter: (params) => !!params.data?.annotated,
    },
    { headerName: 'Total Tokens', width: 110,
      valueGetter: (params) => {
        const sum = (params.data?.annotated?.analysisSummaryTokensConsumed ?? 0)
          + (params.data?.annotated?.developmentWorkTokensConsumed ?? 0);
        return (sum) ? sum : '';
      },
    },

    { headerName: 'Has Analysis', width: 110, cellDataType: 'boolean',
      valueGetter: (params) => !!params.data?.annotated?.analysisSummary,
    },
    { headerName: 'Analysis Updated', field: 'annotated.analysisSummaryLastUpdateTime', width: 130 },
    { headerName: 'Analysis Tokens', width: 110,
      valueGetter: (params) => {
        const tokens = params.data?.annotated?.analysisSummaryTokensConsumed;
        return (tokens) ? tokens : '';
      },
    },


    { headerName: 'Has Dev Work', width: 110, cellDataType: 'boolean',
      valueGetter: (params) => !!params.data?.annotated?.developmentWorkDescribed,
    },
    { headerName: 'Dev Work Updated', field: 'annotated.developmentWorkLastUpdateTime', width: 130 },
    { headerName: 'Dev Tokens', width: 110,
      valueGetter: (params) => {
        const tokens = params.data?.annotated?.developmentWorkTokensConsumed;
        return (tokens) ? tokens : '';
      },
    },

    { headerName: 'Has Personal Interest', width: 130, cellDataType: 'boolean',
      valueGetter: (params) => !!params.data?.annotated?.personalInterrestComment,
    },
    { headerName: 'Personal Interest Priority', field: 'annotated.personalInterrestPriority10', width: 150 },
  ];

  private gridApi?: GridApi<JiraIssueDTO>;

  onGridReady(event: GridReadyEvent<JiraIssueDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  /** Maps an issue type to itself if it is a known enum option, or to the "(others)" bucket otherwise. */
  private typeFilterKey(type: string | undefined): string {
    const value = type ?? '';
    return KNOWN_TYPES.has(value) ? value : OTHER_TYPES;
  }

  /** Maps a resolution value to itself if it is a known enum option, or to the "(others)" bucket otherwise. */
  private resolutionFilterKey(resolution: string | undefined): string {
    const value = resolution ?? '';
    return KNOWN_RESOLUTIONS.has(value) ? value : OTHER_RESOLUTIONS;
  }

  isExternalFilterPresent = (): boolean => {
    const c = this.criteria;
    return c.fromYear != null
      || c.toYear != null
      || (c.usernamePattern ?? '').trim().length > 0
      || c.fromNumber != null
      || c.toNumber != null
      || (c.keyPattern ?? '').trim().length > 0
      || this.parseCsvList(c.summaryContains).length > 0
      || this.parseCsvList(c.descriptionContains).length > 0
      || this.parseCsvList(c.authorContains).length > 0
      || this.parseCsvList(c.commentsContains).length > 0
      || this.parseCsvList(c.excludedResolutions).length > 0
      || this.parseCsvList(c.excludedStatuses).length > 0
      || this.parseCsvList(c.excludedPriorities).length > 0
      || this.parseCsvList(c.excludedTypes).length > 0
      || this.parseCsvList(c.commentAuthorContains).length > 0
      || this.parseCsvList(c.labelsContains).length > 0
      || (c.pullRequestAvailableLabel ?? 'any') !== 'any'
      || this.parseCsvList(c.componentsContains).length > 0
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

  doesExternalFilterPass = (node: IRowNode<JiraIssueDTO>): boolean => {
    const fields = node.data?.fields;
    const c = this.criteria;
    if (!fields) {
      return true;
    }
    if (!this.matchesYearRange(fields.created)) {
      return false;
    }
    if (!this.matchesUsernamePattern(c.usernamePattern, fields.creator, fields.reporter)) {
      return false;
    }
    if (!this.matchesKeyPattern(c.keyPattern, node.data?.key)) {
      return false;
    }
    if (!this.matchesKeyNumberRange(c.fromNumber, c.toNumber, node.data?.key)) {
      return false;
    }
    if (!this.matchesAny(c.summaryContains, fields.summary)) {
      return false;
    }
    if (!this.matchesAny(c.descriptionContains, fields.description)) {
      return false;
    }
    if (!this.matchesAny(c.authorContains, fields.creator, fields.reporter)) {
      return false;
    }
    if (this.isExcluded(c.excludedResolutions, this.resolutionFilterKey(fields.resolution))) {
      return false;
    }
    if (fields.status != null && this.isExcluded(c.excludedStatuses, fields.status)) {
      return false;
    }
    if (fields.priority != null && this.isExcluded(c.excludedPriorities, fields.priority)) {
      return false;
    }
    if (this.isExcluded(c.excludedTypes, this.typeFilterKey(fields.issuetype))) {
      return false;
    }
    const labels = fields.labels ?? [];
    if (!this.matchesAny(c.labelsContains, ...labels)) {
      return false;
    }
    if (!this.matchesAvailability(c.pullRequestAvailableLabel, labels.includes(PULL_REQUEST_AVAILABLE_LABEL))) {
      return false;
    }
    const components = fields.components ?? [];
    if (!this.matchesAny(c.componentsContains, ...components)) {
      return false;
    }
    const comments = fields.comments ?? [];
    if (!this.matchesAny(c.commentsContains, ...comments.map((cm) => cm.body))) {
      return false;
    }
    if (!this.matchesAny(c.commentAuthorContains, ...comments.map((cm) => cm.author))) {
      return false;
    }
    const annotated = node.data?.annotated;
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

  /** Whether the created date's year falls within [fromYear, toYear] (inclusive); unparsable/missing dates pass through. */
  private matchesYearRange(created: string | undefined): boolean {
    if (created == null) {
      return true;
    }
    const year = parseInt(created.substring(0, 4), 10);
    if (isNaN(year)) {
      return true;
    }
    const fromYear = this.criteria.fromYear;
    const toYear = this.criteria.toYear;
    return (fromYear == null || year >= fromYear) && (toYear == null || year <= toYear);
  }

  /** Matches the given regex (full match) against the creator, falling back to reporter then "unknown", mirroring the server-side filter. */
  private matchesUsernamePattern(patternText: string | undefined, creator: string | undefined, reporter: string | undefined): boolean {
    const name = (creator && creator.trim()) ? creator : ((reporter && reporter.trim()) ? reporter : 'unknown');
    return this.matchesRegex(patternText, name);
  }

  private matchesKeyPattern(patternText: string | undefined, key: string | undefined): boolean {
    return this.matchesRegex(patternText, key);
  }

  /** Whether the numeric suffix of the key (eg "123" in "PROJ-123") falls within [fromNumber, toNumber] (inclusive, either bound optional). */
  private matchesKeyNumberRange(fromNumber: number | undefined, toNumber: number | undefined, key: string | undefined): boolean {
    if (fromNumber == null && toNumber == null) {
      return true;
    }
    const number = this.issueNumberOf(key);
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

  private issueNumberOf(key: string | undefined): number | undefined {
    if (!key) {
      return undefined;
    }
    const dashIdx = key.lastIndexOf('-');
    if (dashIdx < 0 || dashIdx === key.length - 1) {
      return undefined;
    }
    const n = Number(key.substring(dashIdx + 1));
    return Number.isFinite(n) ? n : undefined;
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
