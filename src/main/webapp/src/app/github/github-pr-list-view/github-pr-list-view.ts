import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import type { CellClickedEvent, ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import { GitHubPrCriteriaDTO, GitHubPullRequestDTO } from '../../rest';
import { GithubPrCriteria } from '../service/GithubPrCriteria';
import { ResizableHeightDirective } from '../../utils/resizable-height.directive';

/** The ag-grid list of pull requests: column definitions, and the client-side filtering logic driven
 * by the {@link GitHubPrCriteriaDTO} criteria (owned by the sibling GithubPrSearchCriteriaView). */
@Component({
  imports: [AgGridAngular, ResizableHeightDirective],
  selector: 'app-github-pr-list-view',
  templateUrl: './github-pr-list-view.html',
})
export class GithubPrListView {

  @Input() rowData: GitHubPullRequestDTO[] = [];
  @Input() criteria: GitHubPrCriteriaDTO = {};
  @Output() readonly rowSelected = new EventEmitter<GitHubPullRequestDTO>();

  // Column Definitions: Defines the columns to be displayed.
  colDefs: ColDef<GitHubPullRequestDTO>[] = [
    { headerName: 'Number', field: 'number', width: 100,
      cellStyle: { cursor: 'pointer', textDecoration: 'underline' },
      onCellClicked: (params: CellClickedEvent<GitHubPullRequestDTO>) => {
        if (params.data) {
          this.rowSelected.emit(params.data);
        }
      },
    },
    { headerName: 'State', field: 'state', width: 90 },
    { headerName: 'Draft', field: 'draft', width: 80, cellDataType: 'boolean' },
    { headerName: 'Merged', field: 'merged', width: 90, cellDataType: 'boolean' },
    { headerName: 'Title', field: 'title', width: 400 },
    { headerName: 'Body', field: 'body', width: 250, hide: true },
    { headerName: 'Author', field: 'authorLogin', width: 120 },
    { headerName: 'Assignees', width: 150,
      valueGetter: (params) => (params.data?.assigneeLogins ?? []).join(', '),
    },
    { headerName: 'Requested Reviewers', width: 160,
      valueGetter: (params) => (params.data?.requestedReviewerLogins ?? []).join(', '),
    },
    { headerName: 'Labels', width: 180,
      valueGetter: (params) => (params.data?.labelNames ?? []).join(', '),
    },
    { headerName: 'Milestone', field: 'milestoneTitle', width: 120 },
    { headerName: 'Created', field: 'createdAt', width: 100 },
    { headerName: 'Updated', field: 'updatedAt', width: 100 },
    { headerName: 'Closed', field: 'closedAt', width: 100, },
    { headerName: 'Merged At', field: 'mergedAt', width: 100, },
    { headerName: 'Head Ref', field: 'headRef', width: 140 },
    { headerName: 'Base Ref', field: 'baseRef', width: 120 },
    { headerName: 'Mergeable', field: 'mergeable', width: 100, cellDataType: 'boolean' },
    { headerName: 'Mergeable State', field: 'mergeableState', width: 130 },
    { headerName: 'Merged By', field: 'mergedByLogin', width: 120 },
    { headerName: 'Comments', field: 'comments', width: 100 },

    { headerName: 'Review Comments', field: 'reviewComments', width: 130, },
    { headerName: 'Review Comments Count', width: 100,
      hide: false, // FOR DEBUG
      valueGetter: (params) => (params.data?.reviewCommentsData ?? []).length,
    },
    { headerName: 'Diff Review Comments Count-List', width: 130,
      hide: false, // FOR DEBUG
      valueGetter: (params) => {
        const expected = params.data?.comments || 0;
        const fetched = (params.data?.reviewCommentsData ?? []).length;
        const diff = expected - fetched;
        return (diff)? diff : '';
      },
    },

    { headerName: 'Commits', field: 'commits', width: 90, },
    { headerName: 'Additions', field: 'additions', width: 100, },
    { headerName: 'Deletions', field: 'deletions', width: 100, },
    { headerName: 'Changed Files', field: 'changedFiles', width: 110, },
  ];

  private gridApi?: GridApi<GitHubPullRequestDTO>;

  onGridReady(event: GridReadyEvent<GitHubPullRequestDTO>) {
    this.gridApi = event.api;
  }

  onFilterInputsChanged() {
    this.gridApi?.onFilterChanged();
  }

  /** Rows currently passing the client-side external filter, i.e. what the grid actually shows: unlike
   * `rowData`, this reflects in-place criteria edits that haven't been re-queried from the server yet. */
  getDisplayedRows(): GitHubPullRequestDTO[] {
    const rows: GitHubPullRequestDTO[] = [];
    this.gridApi?.forEachNodeAfterFilter((node) => {
      if (node.data) {
        rows.push(node.data);
      }
    });
    return rows;
  }

  isExternalFilterPresent = (): boolean => {
    return GithubPrCriteria.anyCriteriaSet(this.criteria);
  };

  doesExternalFilterPass = (node: IRowNode<GitHubPullRequestDTO>): boolean => {
    const pr = node.data;
    if (!pr) {
      return true;
    }
    return GithubPrCriteria.match(this.criteria, pr);
  };

}
