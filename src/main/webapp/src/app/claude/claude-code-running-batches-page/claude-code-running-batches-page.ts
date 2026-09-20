import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, GridApi, GridReadyEvent } from 'ag-grid-community';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { ClaudeCodeService } from '../../rest/api/claudeCode.service';
import { ResizableHeightDirective } from '../../utils/resizable-height.directive';
import {ClaudeCodePromptRunningBatchDTO} from '../../rest';

const POLL_INTERVAL_MS = 2000;

/** Polls {@code GET .../claude-code/calls} and displays the currently-running {@code claude} CLI
 * prompt invocations in an ag-grid, refreshed every {@link POLL_INTERVAL_MS}. */
@Component({
  imports: [AgGridAngular, ResizableHeightDirective],
  selector: 'app-claude-code-running-batches-page',
  templateUrl: './claude-code-running-batches-page.html',
})
export class ClaudeCodeRunningBatchesPage implements OnInit, OnDestroy {

  readonly calls = signal<ClaudeCodePromptRunningBatchDTO[]>([]);

  readonly suspended = signal(false);

  // The call currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedCall = signal<ClaudeCodePromptRunningBatchDTO | undefined>(undefined);

  colDefs: ColDef<ClaudeCodePromptRunningBatchDTO>[] = [
    { headerName: 'Id', field: 'id', width: 160,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      onCellClicked: (params: CellClickedEvent<ClaudeCodePromptRunningBatchDTO>) => {
        if (params.data) {
          this.selectedCall.set(params.data);
        }
      },
    },
    { headerName: 'Pid', field: 'pid', width: 100 },
    { headerName: 'Started', width: 180,
      valueGetter: (params) => params.data?.startTime,
      valueFormatter: (params) => this.formatTime(params.value),
    },
    { headerName: 'Elapsed', width: 110,
      valueGetter: (params) => params.data?.startTime,
      valueFormatter: (params) => this.formatElapsed(params.value),
    },
    { headerName: 'Prompt', field: 'prompt', width: 300, flex: 1 },
    { headerName: 'Allowed Tools', width: 400,
      valueGetter: (params) => (params.data?.allowedTools ?? []).join(', '),
    },
  ];

  private gridApi?: GridApi<ClaudeCodePromptRunningBatchDTO>;
  private pollSubscription?: Subscription;

  constructor(private claudeCodeService: ClaudeCodeService) {}

  ngOnInit() {
    this.resume();
  }

  ngOnDestroy() {
    this.suspend();
  }

  toggleSuspend() {
    if (this.suspended()) {
      this.resume();
    } else {
      this.suspend();
    }
  }

  resume() {
    if (this.pollSubscription) {
      return;
    }
    this.suspended.set(false);
    this.pollSubscription = timer(0, POLL_INTERVAL_MS)
      .pipe(switchMap(() => this.claudeCodeService.getCurrentRunningBatches()))
      .subscribe({
        next: (calls) => this.calls.set(calls),
        error: (err) => console.error('failed to poll claude-code current calls', err),
      });
  }

  suspend() {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = undefined;
    this.suspended.set(true);
  }

  closeDetail() {
    this.selectedCall.set(undefined);
  }

  onGridReady(event: GridReadyEvent<ClaudeCodePromptRunningBatchDTO>) {
    this.gridApi = event.api;
  }

  formatTime(startTime: number | undefined): string {
    return startTime != null ? new Date(startTime).toLocaleString() : '';
  }

  formatElapsed(startTime: number | undefined): string {
    if (startTime == null) {
      return '';
    }
    return Math.max(0, Math.round((Date.now() - startTime) / 1000)) + 's';
  }
}
