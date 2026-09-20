import { Component, OnInit, signal } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, GridApi, GridReadyEvent } from 'ag-grid-community';
import { ClaudeCodeService } from '../../rest/api/claudeCode.service';
import { ResizableHeightDirective } from '../../utils/resizable-height.directive';
import { ClaudeCodeBatchCriteriaDTO, ClaudeCodePromptBatchDTO } from '../../rest';
import { DateRangeFilter } from '../../jira/issues-search-page/filters/date-range-filter';
import { TextContainsFilter } from '../../jira/issues-search-page/filters/text-contains-filter';
import { NumberRangeFilter } from '../../jira/issues-search-page/filters/number-range-filter';

/** Lists the finished {@code claude} CLI prompt invocations persisted by
 * {@code ClaudeCodePromptBatchRepository}, matching the Search Criteria panel, fetched via
 * {@code POST .../claude-code/query}. */
@Component({
  imports: [AgGridAngular, ResizableHeightDirective, DateRangeFilter, TextContainsFilter, NumberRangeFilter],
  selector: 'app-claude-code-prompt-batches-page',
  templateUrl: './claude-code-batches-page.html',
})
export class ClaudeCodeBatchesPage implements OnInit {

  // Batches shown in the grid, as returned by the last /query call, sorted by startTime desc.
  readonly batches = signal<ClaudeCodePromptBatchDTO[]>([]);

  readonly loading = signal(false);

  // Search criteria, sent as-is to POST .../claude-code/query.
  fromDate = '';
  toDate = '';
  promptContains = '';
  outputResultContains = '';
  minOutputTokens: number | null = null;
  maxOutputTokens: number | null = null;

  // The batch currently shown in the master-detail panel below the grid, or undefined when closed.
  readonly selectedBatch = signal<ClaudeCodePromptBatchDTO | undefined>(undefined);

  colDefs: ColDef<ClaudeCodePromptBatchDTO>[] = [
    { headerName: 'Started', width: 180,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      valueGetter: (params) => params.data?.startTime,
      valueFormatter: (params) => this.formatTime(params.value),
      onCellClicked: (params: CellClickedEvent<ClaudeCodePromptBatchDTO>) => {
        if (params.data) {
          this.selectedBatch.set(params.data);
        }
      },
    },
    { headerName: 'Elapsed', width: 110,
      valueGetter: (params) => params.data?.elapsedSeconds,
      valueFormatter: (params) => this.formatElapsed(params.value),
    },
    { headerName: 'Prompt', flex: 1, field: 'prompt' },
    { headerName: 'Output', flex: 1, field: 'outputResult' },
    { headerName: 'Cost ($)', width: 110,
      valueGetter: (params) => params.data?.total_cost_usd,
      valueFormatter: (params) => params.value != null ? params.value.toFixed(4) : '',
    },
    { headerName: 'Input Tokens', width: 130, field: 'input_tokens' },
    { headerName: 'Cache Creation Tokens', width: 160, field: 'cache_creation_input_tokens' },
    { headerName: 'Cache Read Tokens', width: 150, field: 'cache_read_input_tokens' },
    { headerName: 'Output Tokens', width: 130, field: 'output_tokens' },
    { headerName: 'Thinking Tokens', width: 140, field: 'output_thinking_tokens' },
    { headerName: 'Turns', width: 90,
      valueGetter: (params) => params.data?.details?.num_turns,
    },
    { headerName: 'Error', width: 90,
      valueGetter: (params) => params.data?.details?.is_error,
    },
  ];

  private gridApi?: GridApi<ClaudeCodePromptBatchDTO>;

  constructor(private claudeCodeService: ClaudeCodeService) {}

  ngOnInit() {
    this.reload();
  }

  reload() {
    this.onSearch();
  }

  onSearch() {
    this.loading.set(true);
    const criteria: ClaudeCodeBatchCriteriaDTO = {
      fromDate: this.fromDate || undefined,
      toDate: this.toDate || undefined,
      promptContains: this.promptContains || undefined,
      outputResultContains: this.outputResultContains || undefined,
      minOutputTokens: this.minOutputTokens ?? undefined,
      maxOutputTokens: this.maxOutputTokens ?? undefined,
    };
    this.claudeCodeService.queryPromptBatches(criteria).subscribe({
      next: (batches) => {
        this.batches.set(batches.slice().sort((a, b) => (b.startTime ?? 0) - (a.startTime ?? 0)));
        this.loading.set(false);
      },
      error: (err) => {
        console.error('failed to query claude-code prompt batches', err);
        this.loading.set(false);
      },
    });
  }

  closeDetail() {
    this.selectedBatch.set(undefined);
  }

  onGridReady(event: GridReadyEvent<ClaudeCodePromptBatchDTO>) {
    this.gridApi = event.api;
  }

  formatTime(startTime: number | undefined): string {
    return startTime != null ? new Date(startTime).toLocaleString() : '';
  }

  formatElapsed(elapsedSeconds: number | undefined): string {
    return elapsedSeconds != null ? Math.round(elapsedSeconds) + 's' : '';
  }
}
