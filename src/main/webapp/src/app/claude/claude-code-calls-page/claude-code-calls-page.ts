import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, GridApi, GridReadyEvent } from 'ag-grid-community';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { ClaudeCodeService } from '../../rest/api/claudeCode.service';
import { ClaudeCodePromptCallDTO } from '../../rest/model/claudeCodePromptCallDTO';
import { ResizableHeightDirective } from '../../utils/resizable-height.directive';

const POLL_INTERVAL_MS = 2000;

/** Polls {@code GET .../claude-code/calls} and displays the currently-running {@code claude} CLI
 * prompt invocations in an ag-grid, refreshed every {@link POLL_INTERVAL_MS}. */
@Component({
  imports: [AgGridAngular, ResizableHeightDirective],
  selector: 'app-claude-code-calls-page',
  templateUrl: './claude-code-calls-page.html',
})
export class ClaudeCodeCallsPage implements OnInit, OnDestroy {

  readonly calls = signal<ClaudeCodePromptCallDTO[]>([]);

  readonly suspended = signal(false);

  // The call currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedCall = signal<ClaudeCodePromptCallDTO | undefined>(undefined);

  colDefs: ColDef<ClaudeCodePromptCallDTO>[] = [
    { headerName: 'Id', field: 'id', width: 160,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      onCellClicked: (params: CellClickedEvent<ClaudeCodePromptCallDTO>) => {
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
    { headerName: 'Allowed Tools', width: 220,
      valueGetter: (params) => (params.data?.allowedTools ?? []).join(', '),
    },
    { headerName: 'Prompt', flex: 1, field: 'prompt' },
  ];

  private gridApi?: GridApi<ClaudeCodePromptCallDTO>;
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
      .pipe(switchMap(() => this.claudeCodeService.getCurrentCalls()))
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

  onGridReady(event: GridReadyEvent<ClaudeCodePromptCallDTO>) {
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
