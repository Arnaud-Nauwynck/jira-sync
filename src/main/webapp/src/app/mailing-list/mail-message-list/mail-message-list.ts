import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AgGridAngular } from 'ag-grid-angular';
import type { ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule } from '@ng-bootstrap/ng-bootstrap';
import { MailMessageDTO } from '../../rest';
import { MailMessagesDataService } from './mail-messages-data.service';
import { MailMessageView } from '../mail-message-view/mail-message-view';
import { TextContainsFilter } from '../../jira/issues-list/filters/text-contains-filter';
import { NumberRangeFilter } from '../../jira/issues-list/filters/number-range-filter';
import { DateRangeFilter } from '../../jira/issues-list/filters/date-range-filter';
import { AvailabilityFilter, AvailabilityFilterComponent } from '../../jira/issues-list/filters/availability-filter';

@Component({
  imports: [
    AgGridAngular, FormsModule, NgbCollapseModule, MailMessageView,
    TextContainsFilter, NumberRangeFilter, DateRangeFilter, AvailabilityFilterComponent,
  ],
  selector: 'app-mail-message-list',
  templateUrl: './mail-message-list.html',
})
export class MailMessageList implements OnInit {

  // The message currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedMessage = signal<MailMessageDTO | undefined>(undefined);

  // Data fetching panel: collapsible, expanded by default.
  isDataFetchingCollapsed = false;
  fromMonth = '';
  toMonth = '';
  fromPattern = '';
  subjectPattern = '';
  bodyPattern = '';

  // Main criteria panel: collapsible, expanded by default.
  isMainCriteriaCollapsed = false;

  // Row filter criteria (client-side, applied via ag-grid external filter).
  subjectContains = '';
  bodyContains = '';
  fromContains = '';
  toCcContains = '';

  // Analysis criteria panel: collapsible, collapsed by default.
  isAnalysisCriteriaCollapsed = true;
  analysisSummaryContains = '';
  analysisSummaryUpdatedFrom = '';
  analysisSummaryUpdatedTo = '';
  analysisSummaryMinTokensK: number | null = null;
  analysisSummaryMaxTokensK: number | null = null;
  analysisUserExtraPromptsContains = '';
  analysisAvailability: AvailabilityFilter = 'any';

  // Development work criteria panel: collapsible, collapsed by default.
  isDevelopmentWorkCriteriaCollapsed = true;
  developmentWorkDescribedContains = '';
  developmentWorkUpdatedFrom = '';
  developmentWorkUpdatedTo = '';
  developmentWorkMinTokensK: number | null = null;
  developmentWorkMaxTokensK: number | null = null;
  developmentWorkUserExtraPromptsContains = '';
  developmentWorkAvailability: AvailabilityFilter = 'any';

  // Personal interest criteria panel: collapsible, collapsed by default.
  isPersonalInterestCriteriaCollapsed = true;
  personalInterrestCommentContains = '';
  personalInterrestMinPriority: number | null = null;
  personalInterrestMaxPriority: number | null = null;
  personalInterrestAvailability: AvailabilityFilter = 'any';

  // Column Definitions: Defines the columns to be displayed.
  colDefs: ColDef<MailMessageDTO>[] = [
    { headerName: 'Date', field: 'date', width: 160,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      onCellClicked: (params) => {
        if (params.data) {
          this.selectedMessage.set(params.data);
        }
      },
    },
    { headerName: 'Subject', field: 'subject', width: 420 },
    { headerName: 'From', field: 'from', width: 220 },
    { headerName: 'To', width: 200,
      valueGetter: (params) => (params.data?.to ?? []).join(', '),
    },
    { headerName: 'Cc', width: 200, hide: true,
      valueGetter: (params) => (params.data?.cc ?? []).join(', '),
    },
    { headerName: 'In-Reply-To', field: 'inReplyTo', width: 160, hide: true },
    { headerName: 'References', width: 100, hide: true,
      valueGetter: (params) => (params.data?.references ?? []).length,
    },
    { headerName: 'Message-ID', field: 'messageId', width: 260, hide: true },
    { headerName: 'Body', field: 'bodyText', width: 300, hide: true },
  ];

  private gridApi?: GridApi<MailMessageDTO>;

  constructor(readonly messagesDataService: MailMessagesDataService, private router: Router) {}

  ngOnInit() {
    this.search();
  }

  closeDetail() {
    this.selectedMessage.set(undefined);
  }

  openDetailAsRoute() {
    const messageId = this.selectedMessage()?.messageId;
    if (messageId) {
      this.router.navigate(['/mailing-list-message', messageId]);
    }
  }

  onGridReady(event: GridReadyEvent<MailMessageDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  isExternalFilterPresent = (): boolean => {
    return this.parseCsvList(this.subjectContains).length > 0
      || this.parseCsvList(this.bodyContains).length > 0
      || this.parseCsvList(this.fromContains).length > 0
      || this.parseCsvList(this.toCcContains).length > 0
      || this.parseCsvList(this.analysisSummaryContains).length > 0
      || this.analysisSummaryUpdatedFrom.length > 0
      || this.analysisSummaryUpdatedTo.length > 0
      || this.analysisSummaryMinTokensK != null
      || this.analysisSummaryMaxTokensK != null
      || this.parseCsvList(this.analysisUserExtraPromptsContains).length > 0
      || this.analysisAvailability !== 'any'
      || this.parseCsvList(this.developmentWorkDescribedContains).length > 0
      || this.developmentWorkUpdatedFrom.length > 0
      || this.developmentWorkUpdatedTo.length > 0
      || this.developmentWorkMinTokensK != null
      || this.developmentWorkMaxTokensK != null
      || this.parseCsvList(this.developmentWorkUserExtraPromptsContains).length > 0
      || this.developmentWorkAvailability !== 'any'
      || this.parseCsvList(this.personalInterrestCommentContains).length > 0
      || this.personalInterrestMinPriority != null
      || this.personalInterrestMaxPriority != null
      || this.personalInterrestAvailability !== 'any';
  };

  doesExternalFilterPass = (node: IRowNode<MailMessageDTO>): boolean => {
    const msg = node.data;
    if (!msg) {
      return true;
    }
    if (!this.matchesAny(this.subjectContains, msg.subject)) {
      return false;
    }
    if (!this.matchesAny(this.bodyContains, msg.bodyText)) {
      return false;
    }
    if (!this.matchesAny(this.fromContains, msg.from)) {
      return false;
    }
    if (!this.matchesAny(this.toCcContains, ...(msg.to ?? []), ...(msg.cc ?? []))) {
      return false;
    }
    const annotated = msg.annotated;
    if (!this.matchesAvailability(this.analysisAvailability, !!annotated?.analysisSummary)) {
      return false;
    }
    if (!this.matchesAny(this.analysisSummaryContains, annotated?.analysisSummary)) {
      return false;
    }
    if (!this.matchesDateRange(this.analysisSummaryUpdatedFrom, this.analysisSummaryUpdatedTo, annotated?.analysisSummaryLastUpdateTime)) {
      return false;
    }
    if (!this.matchesTokensRangeK(this.analysisSummaryMinTokensK, this.analysisSummaryMaxTokensK, annotated?.analysisSummaryTokensConsumed)) {
      return false;
    }
    if (!this.matchesAny(this.analysisUserExtraPromptsContains, ...(annotated?.analysisUserExtraPrompts ?? []))) {
      return false;
    }
    if (!this.matchesAvailability(this.developmentWorkAvailability, !!annotated?.developmentWorkDescribed)) {
      return false;
    }
    if (!this.matchesAny(this.developmentWorkDescribedContains, annotated?.developmentWorkDescribed)) {
      return false;
    }
    if (!this.matchesDateRange(this.developmentWorkUpdatedFrom, this.developmentWorkUpdatedTo, annotated?.developmentWorkLastUpdateTime)) {
      return false;
    }
    if (!this.matchesTokensRangeK(this.developmentWorkMinTokensK, this.developmentWorkMaxTokensK, annotated?.developmentWorkTokensConsumed)) {
      return false;
    }
    if (!this.matchesAny(this.developmentWorkUserExtraPromptsContains, ...(annotated?.developmentWorkUserExtraPrompts ?? []))) {
      return false;
    }
    if (!this.matchesAvailability(this.personalInterrestAvailability, !!annotated?.personalInterrestComment)) {
      return false;
    }
    if (!this.matchesAny(this.personalInterrestCommentContains, annotated?.personalInterrestComment)) {
      return false;
    }
    if (!this.matchesNumberRange(this.personalInterrestMinPriority, this.personalInterrestMaxPriority, annotated?.personalInterrestPriority10)) {
      return false;
    }
    return true;
  };

  private matchesAvailability(filter: AvailabilityFilter, present: boolean): boolean {
    if (filter === 'yes') {
      return present;
    }
    if (filter === 'no') {
      return !present;
    }
    return true;
  }

  private matchesDateRange(from: string, to: string, value: string | undefined): boolean {
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

  private matchesNumberRange(min: number | null, max: number | null, value: number | undefined): boolean {
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
  private matchesTokensRangeK(minK: number | null, maxK: number | null, value: number | undefined): boolean {
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

  private matchesAny(filterValue: string, ...values: (string | undefined)[]): boolean {
    const terms = this.parseCsvList(filterValue);
    if (terms.length === 0) {
      return true;
    }
    return values.some((value) =>
      value != null && terms.some((term) => value.toLowerCase().includes(term.toLowerCase())));
  }

  private parseCsvList(value: string): string[] {
    return (value ?? '')
      .split(',')
      .map((term) => term.trim())
      .filter((term) => term.length > 0);
  }

  search() {
    this.messagesDataService.search(this.fromMonth, this.toMonth, this.fromPattern, this.subjectPattern, this.bodyPattern);
  }

}
