import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AgGridAngular } from 'ag-grid-angular';
import type { ColDef, GridApi, GridReadyEvent, IRowNode } from 'ag-grid-community';
import {ChangeLogEvent, GithubPRChange, JiraIssueChange, MailingListChange} from '../../rest';
import { EventLogService } from '../event-log.service';
import {Crit} from '../../utils/Crit';

interface EventTypeOption {
  value: string;
  label: string;
}

const EVENT_TYPE_OPTIONS: EventTypeOption[] = [
  { value: 'jiraIssue', label: 'Jira Issue' },
  { value: 'githubPR', label: 'GitHub PR' },
  { value: 'mailingList', label: 'Mailing List' },
];

const CHANGE_TYPE_OPTIONS: string[] = ['create', 'update', 'updateAnnotation', 'removeAnnotation'];

/** The recent-activity page: polls {@link EventLogService} and displays its rolling event window
 * in an ag-grid, with basic filters and a suspend/resume control for the polling itself. */
@Component({
  imports: [AgGridAngular, FormsModule],
  selector: 'app-event-log-page',
  templateUrl: './event-log-page.html',
})
export class EventLogPage implements OnInit, OnDestroy {

  readonly eventTypeOptions = EVENT_TYPE_OPTIONS;
  readonly changeTypeOptions = CHANGE_TYPE_OPTIONS;

  filterEventType = '';
  filterChangeType = '';
  filterText = '';

  colDefs: ColDef<ChangeLogEvent>[] = [
    { headerName: 'Time', field: 'timestamp', width: 200,
      valueFormatter: (params) => this.formatTimestamp(params.value),
    },
    { headerName: 'Type', width: 110,
      valueGetter: (params) => this.typeLabel(params.data!),
    },
    // { headerName: 'Change', field: 'changeType', width: 130 },
    { headerName: 'Entity', width: 140,
      valueGetter: (params) => this.entityOf(params.data!),
    },
    { headerName: 'Details', flex: 1,
      valueGetter: (params) => this.detailsOf(params.data!),
    },
  ];

  private gridApi?: GridApi<ChangeLogEvent>;

  constructor(readonly eventLogService: EventLogService) {}

  ngOnInit() {
    this.eventLogService.resume();
  }

  ngOnDestroy() {
    this.eventLogService.suspend();
  }

  onGridReady(event: GridReadyEvent<ChangeLogEvent>) {
    this.gridApi = event.api;
  }

  toggleSuspend() {
    if (this.eventLogService.suspended()) {
      this.eventLogService.resume();
    } else {
      this.eventLogService.suspend();
    }
  }

  onFilterChanged() {
    this.gridApi?.onFilterChanged();
  }

  isExternalFilterPresent = (): boolean => {
    return !!this.filterEventType || !!this.filterChangeType || !!this.filterText.trim();
  };

  doesExternalFilterPass = (node: IRowNode<ChangeLogEvent>): boolean => {
    const event = node.data;
    if (!event) {
      return false;
    }
    if (this.filterEventType && event.eventType !== this.filterEventType) {
      return false;
    }
    // if (this.filterChangeType && event.changeType !== this.filterChangeType) {
    //   return false;
    // }
    // const text = this.filterText.trim().toLowerCase();
    // if (text) {
    //   const haystack = `${this.entityOf(event)} ${this.detailsOf(event)}`.toLowerCase();
    //   if (!haystack.includes(text)) {
    //     return false;
    //   }
    // }
    return true;
  };

  typeLabel(event: ChangeLogEvent): string {
    return EVENT_TYPE_OPTIONS.find((o) => o.value === event?.eventType)?.label ?? (event?.eventType ?? '');
  }

  entityOf(event: ChangeLogEvent): string {
    if (!event) {
      return '';
    }
    switch (event.eventType) {
      case 'jiraIssue': {
        const event2 = <JiraIssueChange>event;
        return '' + event2.issueKey;
      }
      case 'githubPR': {
        const event2 = <GithubPRChange>event;
        return '' + event2.number;
      }
      case 'mailingList': {
        const event2 = <MailingListChange> event;
        return event2.subject!;
      }
      default: return '';
    }
  }

  detailsOf(event: ChangeLogEvent): string {
    switch (event.eventType) {
      case 'jiraIssue': {
        const event2 = <JiraIssueChange>event;
        return 'Jira Issue Changed ' + event2.issueKey;
      }
      case 'githubPR': {
        const event2 = <GithubPRChange>event;
        return 'Github PR Changed ' + event2.number;
      }
      case 'mailingList': {
        const event2 = <MailingListChange> event;
        return 'Mail ' + event2.subject!;
      }
    }
    return '';
  }

  formatTimestamp(isoDateTime: string | undefined): string {
    return isoDateTime ? new Date(isoDateTime).toLocaleString() : '';
  }
}
