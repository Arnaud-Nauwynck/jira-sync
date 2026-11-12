import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { AgGridAngular } from 'ag-grid-angular';
import type { ColDef } from 'ag-grid-community';
import { HighchartsChartComponent } from 'highcharts-angular';
import type * as Highcharts from 'highcharts';
import { ComponentTimelineDTO, IssuesTimelineDTO, IssuesTimelineQueryDTO, JiraIssuesService } from '../../rest';
import { JiraReferentialDataService } from '../service/jira-referential-data.service';
import { DateRangeFilter } from '../issues-search-page/filters/date-range-filter';
import { ExcludeDropdownFilter, ExcludeFilterOption } from '../issues-search-page/filters/exclude-dropdown-filter';
import { IssuesCreatedTimelineBuilder } from './issues-created-timeline-builder';

const DEFAULT_PERIOD_MONTHS = 3;

interface BucketByOption {
  value: IssuesTimelineQueryDTO.BucketByEnum;
  label: string;
}

const BUCKET_BY_OPTIONS: BucketByOption[] = [
  { value: IssuesTimelineQueryDTO.BucketByEnum.OneDay, label: '1 day' },
  { value: IssuesTimelineQueryDTO.BucketByEnum.OneWeek, label: '1 week' },
  { value: IssuesTimelineQueryDTO.BucketByEnum.OneMonth, label: '1 month' },
  { value: IssuesTimelineQueryDTO.BucketByEnum.ThreeMonths, label: '3 months' },
];

const ISSUE_TYPES = ['Bug', 'Improvement', 'New Feature', 'Story', 'Epic', 'Sub-task', 'Task', 'Umbrella', 'Question',
  'Wish', 'Test', 'Documentation', 'IT Help', 'Brainstorming', 'Dependency upgrade', 'Request',
  'Planned Work', 'Github Integration', 'RTC', 'Blog - New Blog Request'];

interface IssueTypeCountRow {
  issueType: string;
  count: number;
}

interface ComponentChartSection {
  component: string;
  issueCount: number;
  chartOptions: Highcharts.Options;
  issueTypeCounts: IssueTypeCountRow[];
}

/** Page listing the current issues, filtered by period, project components and issue types,
 * displayed as 1 "issues created per day" timeline section per selected component. */
@Component({
  imports: [FormsModule, DateRangeFilter, ExcludeDropdownFilter, HighchartsChartComponent, AgGridAngular],
  selector: 'app-current-issues-page',
  templateUrl: './current-issues-page.html',
})
export class CurrentIssuesPage {

  private readonly jiraIssuesService = inject(JiraIssuesService);
  private readonly referentialData = inject(JiraReferentialDataService);
  private readonly projectComponents = toSignal(this.referentialData.projectComponents$, { initialValue: [] });

  startDate = CurrentIssuesPage.toIsoDate(CurrentIssuesPage.monthsAgo(DEFAULT_PERIOD_MONTHS));
  endDate = CurrentIssuesPage.toIsoDate(new Date());

  readonly bucketByOptions = BUCKET_BY_OPTIONS;
  bucketBy: IssuesTimelineQueryDTO.BucketByEnum = IssuesTimelineQueryDTO.BucketByEnum.OneDay;

  countOpenedBefore = false;
  creationOnly = true;
  showDetails = false;

  readonly issueTypeCountColDefs: ColDef<IssueTypeCountRow>[] = [
    { headerName: 'Issue Type', field: 'issueType', flex: 1, sort: 'asc' },
    { headerName: 'Count', field: 'count', width: 120, type: 'numericColumn' },
  ];

  readonly componentFilterOptions = computed<ExcludeFilterOption[]>(() =>
    this.projectComponents().map((c) => ({ label: c.name ?? '', value: c.name ?? '' })));
  excludedComponents = new Set<string>();

  readonly issueTypeFilterOptions: ExcludeFilterOption[] = ISSUE_TYPES.map((t) => ({ label: t, value: t }));
  excludedIssueTypes = new Set<string>();

  readonly sections = signal<ComponentChartSection[]>([]);
  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string>('');

  onStartDateChange(value: string) {
    this.startDate = value;
  }

  onEndDateChange(value: string) {
    this.endDate = value;
  }

  onExcludedComponentsChange(excluded: Set<string>) {
    this.excludedComponents = excluded;
  }

  onExcludedIssueTypesChange(excluded: Set<string>) {
    this.excludedIssueTypes = excluded;
  }

  search() {
    this.loading.set(true);
    this.errorMessage.set('');
    const query: IssuesTimelineQueryDTO = {
      startDate: this.startDate,
      endDate: this.endDate,
      bucketBy: this.bucketBy,
      countOpenedBefore: this.countOpenedBefore,
      creationOnly: this.creationOnly,
      excludedComponents: [...this.excludedComponents],
      excludedIssueTypes: [...this.excludedIssueTypes],
    };
    this.jiraIssuesService.queryIssuesTimeline(query).subscribe({
      next: (timeline) => {
        this.sections.set(this.buildSections(timeline, query));
        this.loading.set(false);
      },
      error: (err) => {
        console.error('failed to query issues timeline', err);
        this.errorMessage.set('Failed to load issues timeline');
        this.loading.set(false);
      },
    });
  }

  private buildSections(timeline: IssuesTimelineDTO, query: IssuesTimelineQueryDTO): ComponentChartSection[] {
    const bucketLabel = BUCKET_BY_OPTIONS.find((o) => o.value === query.bucketBy)?.label ?? '';
    return (timeline.components ?? []).map((c) => ({
      component: c.component ?? '',
      issueCount: c.issueCount ?? 0,
      chartOptions: IssuesCreatedTimelineBuilder.build(c, timeline.bucketStarts ?? [], bucketLabel, !!query.countOpenedBefore, !!query.creationOnly),
      issueTypeCounts: CurrentIssuesPage.issueTypeCountsOf(c, !!query.countOpenedBefore || !query.creationOnly),
    }));
  }

  /** Count per issue type: the last value when the series are running totals (opened before / still open), else the sum over the buckets. */
  private static issueTypeCountsOf(c: ComponentTimelineDTO, countOpenedBefore: boolean): IssueTypeCountRow[] {
    return (c.series ?? []).map((s) => {
      const counts = s.counts ?? [];
      const count = countOpenedBefore ? (counts[counts.length - 1] ?? 0) : counts.reduce((a, b) => a + b, 0);
      return { issueType: s.issueType ?? '', count };
    });
  }

  private static monthsAgo(months: number): Date {
    const d = new Date();
    d.setMonth(d.getMonth() - months);
    return d;
  }

  private static toIsoDate(d: Date): string {
    return d.toISOString().substring(0, 10);
  }
}
