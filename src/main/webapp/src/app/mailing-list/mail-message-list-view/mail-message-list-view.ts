import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { MailMessageCriteriaDTO, MailMessageDTO } from '../../rest';
import { MailingListCriteria } from '../service/MailingListCriteria';
import { ResizableHeightDirective } from '../../utils/resizable-height.directive';

/** The ag-grid list of mailing-list messages: column definitions, and the client-side filtering logic
 * driven by the {@link MailMessageCriteriaDTO} criteria (owned by the sibling MailMessageCriteriaView). */
@Component({
  imports: [AgGridAngular, ResizableHeightDirective],
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

  /** Rows currently passing the client-side external filter, i.e. what the grid actually shows: unlike
   * `rowData`, this reflects in-place criteria edits that haven't been re-queried from the server yet. */
  getDisplayedRows(): MailMessageDTO[] {
    const rows: MailMessageDTO[] = [];
    this.gridApi?.forEachNodeAfterFilter((node) => {
      if (node.data) {
        rows.push(node.data);
      }
    });
    return rows;
  }

  isExternalFilterPresent = (): boolean => {
    return MailingListCriteria.anyCriteriaSet(this.criteria);
  };

  doesExternalFilterPass = (node: IRowNode<MailMessageDTO>): boolean => {
    const msg = node.data;
    if (!msg) {
      return true;
    }
    return MailingListCriteria.match(this.criteria, msg);
  };

}
