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

/** One grid row per component: fixed columns, plus 1 count column per issue type. */
interface ComponentCountRow {
  component: string;
  issueCount: number | null;
  [issueType: string]: string | number | null;
}

interface ComponentChartSection {
  component: string;
  issueCount: number;
  chartOptions: Highcharts.Options;
  issueTypeCounts: IssueTypeCountRow[];
}

type TimelineTab = 'all' | 'perComponent' | 'perIssueType' | 'details' | 'counts';

/** Page listing the current issues, filtered by period, project components and issue types,
 * displayed in 3 tabs: total timeline, details timeline per component, current counts grid. */
@Component({
  imports: [FormsModule, DateRangeFilter, ExcludeDropdownFilter, HighchartsChartComponent, AgGridAngular],
  selector: 'app-current-issues-page',
  templateUrl: './issues-timeline-page.component.html',
})
export class IssuesTimelinePage {

  private readonly jiraIssuesService = inject(JiraIssuesService);
  private readonly referentialData = inject(JiraReferentialDataService);
  private readonly projectComponents = toSignal(this.referentialData.projectComponents$, { initialValue: [] });

  startDate = IssuesTimelinePage.toIsoDate(IssuesTimelinePage.monthsAgo(DEFAULT_PERIOD_MONTHS));
  endDate = IssuesTimelinePage.toIsoDate(new Date());

  readonly bucketByOptions = BUCKET_BY_OPTIONS;
  bucketBy: IssuesTimelineQueryDTO.BucketByEnum = IssuesTimelineQueryDTO.BucketByEnum.OneDay;

  countOpenedBefore = false;
  creationOnly = true;

  readonly activeTab = signal<TimelineTab>('all');
  readonly totalChartOptions = signal<Highcharts.Options | null>(null);
  readonly perComponentChartOptions = signal<Highcharts.Options | null>(null);
  readonly perIssueTypeChartOptions = signal<Highcharts.Options | null>(null);

  readonly componentFilterOptions = computed<ExcludeFilterOption[]>(() =>
    this.projectComponents().map((c) => ({ label: c.name ?? '', value: c.name ?? '' })));
  excludedComponents = new Set<string>();

  readonly issueTypeFilterOptions: ExcludeFilterOption[] = ISSUE_TYPES.map((t) => ({ label: t, value: t }));
  excludedIssueTypes = new Set<string>();

  readonly sections = signal<ComponentChartSection[]>([]);
  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string>('');

  /** Count per issue type summed over all component sections. */
  readonly totalIssueTypeCounts = computed<IssueTypeCountRow[]>(() => {
    const sums = new Map<string, number>();
    for (const section of this.sections()) {
      for (const row of section.issueTypeCounts) {
        sums.set(row.issueType, (sums.get(row.issueType) ?? 0) + row.count);
      }
    }
    return [...sums].map(([issueType, count]) => ({ issueType, count }));
  });

  /** Issue types present in any component section, sorted: 1 count column each. */
  readonly countsIssueTypes = computed<string[]>(() =>
    [...new Set(this.sections().flatMap((s) => s.issueTypeCounts.map((t) => t.issueType)))].sort());

  /** Rows of the counts grid: 1 row per component, with the count per issue type (0 when absent). */
  readonly countsRows = computed<ComponentCountRow[]>(() => {
    const issueTypes = this.countsIssueTypes();
    return this.sections().map((s) => {
      const row: ComponentCountRow = { component: s.component, issueCount: s.issueCount };
      for (const issueType of issueTypes) {
        row[issueType] = 0;
      }
      for (const t of s.issueTypeCounts) {
        row[t.issueType] = (row[t.issueType] as number) + t.count;
      }
      return row;
    });
  });

  /** Pinned bottom row of the counts grid: total count per issue type over all components. */
  readonly countsTotalRows = computed<ComponentCountRow[]>(() => {
    const row: ComponentCountRow = { component: 'Total', issueCount: null };
    for (const t of this.totalIssueTypeCounts()) {
      row[t.issueType] = t.count;
    }
    return [row];
  });

  readonly countsColDefs = computed<ColDef<ComponentCountRow>[]>(() => [
    { headerName: 'Component', field: 'component', pinned: 'left', width: 220, sort: 'asc' },
    { headerName: 'Issues', field: 'issueCount', width: 110, type: 'numericColumn' },
    ...this.countsIssueTypes().map((issueType): ColDef<ComponentCountRow> =>
      ({ headerName: issueType, field: issueType, width: 130, type: 'numericColumn' })),
  ]);

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
        const bucketLabel = IssuesTimelinePage.bucketLabelOf(query.bucketBy);
        const countOpenedBefore = !!query.countOpenedBefore;
        const creationOnly = !!query.creationOnly;
        this.totalChartOptions.set(IssuesCreatedTimelineBuilder.buildTotal(timeline, bucketLabel, countOpenedBefore, creationOnly));
        this.perComponentChartOptions.set(IssuesCreatedTimelineBuilder.buildPerComponent(timeline, bucketLabel, countOpenedBefore, creationOnly));
        this.perIssueTypeChartOptions.set(IssuesCreatedTimelineBuilder.buildPerIssueType(timeline, bucketLabel, countOpenedBefore, creationOnly));
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
    const bucketLabel = IssuesTimelinePage.bucketLabelOf(query.bucketBy);
    return (timeline.components ?? [])
      .map((c) => ({
        component: c.component ?? '',
        issueCount: c.issueCount ?? 0,
        chartOptions: IssuesCreatedTimelineBuilder.build(c, timeline.bucketStarts ?? [], bucketLabel, !!query.countOpenedBefore, !!query.creationOnly),
        issueTypeCounts: IssuesTimelinePage.issueTypeCountsOf(c, !!query.countOpenedBefore || !query.creationOnly),
      }))
      .sort((a, b) => b.issueCount - a.issueCount);
  }

  /** Count per issue type: the last value when the series are running totals (opened before / still open), else the sum over the buckets. */
  private static issueTypeCountsOf(c: ComponentTimelineDTO, countOpenedBefore: boolean): IssueTypeCountRow[] {
    return (c.series ?? []).map((s) => {
      const counts = s.counts ?? [];
      const count = countOpenedBefore ? (counts[counts.length - 1] ?? 0) : counts.reduce((a, b) => a + b, 0);
      return { issueType: s.issueType ?? '', count };
    });
  }

  private static bucketLabelOf(bucketBy: IssuesTimelineQueryDTO.BucketByEnum | undefined): string {
    return BUCKET_BY_OPTIONS.find((o) => o.value === bucketBy)?.label ?? '';
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
