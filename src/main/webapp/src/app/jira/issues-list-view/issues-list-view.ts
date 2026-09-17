import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { IssuesCriteriaDTO, JiraIssueDTO } from '../../rest';
import {IssueCriteria} from '../service/IssueCriteria';
import {Crit} from '../../utils/Crit';

export const OTHER_RESOLUTIONS = '(others)';
export const OTHER_TYPES = '(others)';
export const PULL_REQUEST_AVAILABLE_LABEL = 'pull-request-available';

export const KNOWN_TYPES = new Set(['Bug', 'Improvement', 'New Feature', 'Story', 'Epic', 'Sub-task', 'Task', 'Umbrella', 'Question',
  'Wish', 'Test', 'Documentation', 'IT Help', 'Brainstorming', 'Dependency upgrade', 'Request',
  'Planned Work', 'Github Integration', 'RTC', 'Blog - New Blog Request']);

export const KNOWN_RESOLUTIONS = new Set(['Done', 'Fixed', 'Invalid', 'Incomplete', 'Cannot Reproduce', 'Works for Me', 'Not A Problem',
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

  isExternalFilterPresent = (): boolean => {
    return IssueCriteria.anyCriteriaSet(this.criteria);
  };

  doesExternalFilterPass = (node: IRowNode<JiraIssueDTO>): boolean => {
    let src = node.data!;
    const c = this.criteria;
    return IssueCriteria.match(c, src);
  };


}
