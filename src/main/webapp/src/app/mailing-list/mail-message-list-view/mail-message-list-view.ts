import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { MailMessageCriteriaDTO, MailMessageDTO } from '../../rest';

/** The ag-grid list of mailing-list messages: column definitions, and the client-side filtering logic
 * driven by the {@link MailMessageCriteriaDTO} criteria (owned by the sibling MailMessageCriteriaView). */
@Component({
  imports: [AgGridAngular],
  selector: 'app-mail-message-list-view',
  templateUrl: './mail-message-list-view.html',
})
export class MailMessageListView {

  @Input() rowData: MailMessageDTO[] = [];
  @Input() criteria: MailMessageCriteriaDTO = {};
  @Output() readonly rowSelected = new EventEmitter<MailMessageDTO>();

  // Column Definitions: Defines the columns to be displayed.
  colDefs: ColDef<MailMessageDTO>[] = [
    { headerName: 'Date', field: 'date', width: 160,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      onCellClicked: (params) => {
        if (params.data) {
          this.rowSelected.emit(params.data);
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

  onGridReady(event: GridReadyEvent<MailMessageDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  isExternalFilterPresent = (): boolean => {
    const c = this.criteria;
    return this.parseCsvList(c.subjectContains).length > 0
      || this.parseCsvList(c.bodyContains).length > 0
      || this.parseCsvList(c.fromContains).length > 0
      || this.parseCsvList(c.toCcContains).length > 0
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

  doesExternalFilterPass = (node: IRowNode<MailMessageDTO>): boolean => {
    const msg = node.data;
    const c = this.criteria;
    if (!msg) {
      return true;
    }
    if (!this.matchesAny(c.subjectContains, msg.subject)) {
      return false;
    }
    if (!this.matchesAny(c.bodyContains, msg.bodyText)) {
      return false;
    }
    if (!this.matchesAny(c.fromContains, msg.from)) {
      return false;
    }
    if (!this.matchesAny(c.toCcContains, ...(msg.to ?? []), ...(msg.cc ?? []))) {
      return false;
    }
    const annotated = msg.annotated;
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

  private matchesAny(filterValue: string | undefined, ...values: (string | undefined)[]): boolean {
    const terms = this.parseCsvList(filterValue);
    if (terms.length === 0) {
      return true;
    }
    return values.some((value) =>
      value != null && terms.some((term) => value.toLowerCase().includes(term.toLowerCase())));
  }

  private parseCsvList(value: string | undefined): string[] {
    return (value ?? '')
      .split(',')
      .map((term) => term.trim())
      .filter((term) => term.length > 0);
  }

}
