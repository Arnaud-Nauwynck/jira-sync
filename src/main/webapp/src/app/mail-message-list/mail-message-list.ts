import { Component, OnInit, signal } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { FormsModule } from '@angular/forms';
import { NgbCollapseModule } from '@ng-bootstrap/ng-bootstrap';
import { MailMessageDTO } from '../rest/model/mailMessageDTO';
import { MailMessagesDataService } from './mail-messages-data.service';

@Component({
  imports: [AgGridAngular, FormsModule, NgbCollapseModule],
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

  constructor(readonly messagesDataService: MailMessagesDataService) {}

  ngOnInit() {
    this.search();
  }

  closeDetail() {
    this.selectedMessage.set(undefined);
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
      || this.parseCsvList(this.toCcContains).length > 0;
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
    return true;
  };

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
