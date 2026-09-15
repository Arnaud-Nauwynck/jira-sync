import { Component, OnInit, signal } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, ColGroupDef, GridApi, GridReadyEvent, ICellRendererParams, IRowNode, ValueGetterParams } from 'ag-grid-community';
import { FormsModule } from '@angular/forms';
import { MailingListSyncService } from '../../rest';
import { MailMessagesService } from '../../rest';
import { UserMailMessageStatsDTO } from '../../rest';
import { UserMailMessagePerMonthStatsDTO } from '../../rest';
import { MailMessageDTO } from '../../rest';

interface PerMonthStatDef {
  key: keyof UserMailMessagePerMonthStatsDTO;
  header: string;
  width: number;
}

const PER_MONTH_STAT_DEFS: PerMonthStatDef[] = [
  { key: 'messageCount', header: 'Messages', width: 100 },
  { key: 'threadStartedCount', header: 'Threads Started', width: 130 },
  { key: 'replyCount', header: 'Replies', width: 100 },
];

@Component({
  imports: [AgGridAngular, FormsModule],
  selector: 'app-user-mail-message-stat-list',
  templateUrl: './user-mail-message-stat-list.html',
})
export class UserMailMessageStatList implements OnInit {

  // The message currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedMessage = signal<MailMessageDTO | undefined>(undefined);

  fromYear = 2012;
  toYear = 2030;
  fromPattern = '';

  // Row filter criteria (client-side, applied via ag-grid external filter).
  minMessages: number | null = null;
  maxMessages: number | null = null;
  nameContains = '';
  namePattern = '';

  // Row Data: The data to be displayed.
  rowData = signal<UserMailMessageStatsDTO[]>([]);

  // Column Definitions: Defines the columns to be displayed.
  colDefs = signal<(ColDef<UserMailMessageStatsDTO>)[]>([]);

  private gridApi?: GridApi<UserMailMessageStatsDTO>;

  constructor(private mailingListSyncService: MailingListSyncService, private mailMessagesService: MailMessagesService) {}

  ngOnInit() {
    this.search();
  }

  onGridReady(event: GridReadyEvent<UserMailMessageStatsDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  closeDetail() {
    this.selectedMessage.set(undefined);
  }

  isExternalFilterPresent = (): boolean => {
    return this.minMessages != null
      || this.maxMessages != null
      || this.parseCsvList(this.nameContains).length > 0
      || this.parseCsvList(this.namePattern).length > 0;
  };

  doesExternalFilterPass = (node: IRowNode<UserMailMessageStatsDTO>): boolean => {
    const data = node.data;
    if (!data) {
      return true;
    }
    const messageCount = data.messageCount ?? 0;
    if (this.minMessages != null && messageCount < this.minMessages) {
      return false;
    }
    if (this.maxMessages != null && messageCount > this.maxMessages) {
      return false;
    }
    const user = data.user ?? '';
    const containsTerms = this.parseCsvList(this.nameContains);
    if (containsTerms.length > 0
        && !containsTerms.some((term) => user.toLowerCase().includes(term.toLowerCase()))) {
      return false;
    }
    const patterns = this.parseCsvList(this.namePattern);
    if (patterns.length > 0
        && !patterns.some((pattern) => this.matchesPattern(user, pattern))) {
      return false;
    }
    return true;
  };

  private orUndefined(value: string): string | undefined {
    return value != null && value.trim().length > 0 ? value : undefined;
  }

  private parseCsvList(value: string): string[] {
    return (value ?? '')
      .split(',')
      .map((term) => term.trim())
      .filter((term) => term.length > 0);
  }

  private matchesPattern(value: string, pattern: string): boolean {
    try {
      return new RegExp(pattern, 'i').test(value);
    } catch {
      return false;
    }
  }

  search() {
    this.mailingListSyncService.queryUserMailMessageStats(this.fromYear, this.toYear, this.orUndefined(this.fromPattern),
        'body', false, { httpHeaderAccept: 'application/json' as any })
      .subscribe({
        next: (stats) => {
          this.colDefs.set(this.buildColDefs(stats));
          this.rowData.set(stats);
        },
        error: (err) => {
          console.error('failed to load user mail message stats', err)
        },
      });
  }

  private showMessage(messageId: string) {
    this.mailMessagesService.findMessageByMessageId(messageId, 'body', false, { httpHeaderAccept: 'application/json' as any })
      .subscribe({
        next: (msg) => {
          this.selectedMessage.set(msg);
        },
        error: (err) => {
          console.error('failed to load mail message ' + messageId, err)
        },
      });
  }

  private escapeHtml(value: string): string {
    return value.replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch] as string));
  }

  private buildColDefs(stats: UserMailMessageStatsDTO[]): ColDef<UserMailMessageStatsDTO>[] {
    const months = Array.from(new Set(stats.flatMap((s) => Object.keys(s.perMonth ?? {})))).sort();
    const perMonthColDefs: ColGroupDef<UserMailMessageStatsDTO>[] = months.map((month) => ({
      headerName: month,
      children: [
        ...PER_MONTH_STAT_DEFS.map((stat) => ({
          headerName: stat.header,
          width: stat.width,
          valueGetter: (params: ValueGetterParams<UserMailMessageStatsDTO>) => params.data?.perMonth?.[month]?.[stat.key] ?? 0,
        })),
        {
          headerName: 'First Messages',
          width: 320,
          valueGetter: (params: ValueGetterParams<UserMailMessageStatsDTO>) => (params.data?.perMonth?.[month]?.firstMessages ?? []).join(', '),
          cellRenderer: (params: ICellRendererParams<UserMailMessageStatsDTO>) => {
            const ids = ((params.value as string) ?? '').split(',').map((id) => id.trim()).filter((id) => id.length > 0);
            return ids
              .map((id) => `<span class="mail-message-id-link" data-message-id="${this.escapeHtml(id)}" style="cursor:pointer;text-decoration:underline;color:var(--bs-link-color,#0d6efd);margin-right:8px;">${this.escapeHtml(id)}</span>`)
              .join('');
          },
          onCellClicked: (params: CellClickedEvent<UserMailMessageStatsDTO>) => {
            const target = params.event?.target as HTMLElement | null;
            const messageId = target?.closest<HTMLElement>('[data-message-id]')?.dataset['messageId'];
            if (messageId) {
              this.showMessage(messageId);
            }
          },
        },
      ],
    }));
    return [
      { field: 'user', width: 260 },
      { headerName: 'Messages', width: 100, field: 'messageCount', },
      { headerName: 'Threads Started', width: 130, field: 'threadStartedCount', },
      { headerName: 'Replies', width: 100, field: 'replyCount', },
      ...perMonthColDefs,
    ];
  }
}
