import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HighchartsChartComponent } from 'highcharts-angular';
import type * as Highcharts from 'highcharts';
import { AgGridAngular } from 'ag-grid-angular';
import type { ICellRendererAngularComp } from 'ag-grid-angular';
import type { CellValueChangedEvent, ColDef, ColGroupDef, ICellRendererParams, ValueGetterParams } from 'ag-grid-community';
import { UserActivityStatsService } from '../rest';
import { UserActivityStatsDTO, UserActivityMonthStatsDTO } from '../rest';

/**
 * Fixed-order categorical palette (see the dataviz skill's references/palette.md): validated
 * colorblind-safe up to 8 series. Ranks below that fold into a receding gray ramp instead of
 * generating more hues (a 9th generated hue is indistinguishable from an existing one under CVD).
 */
const CATEGORICAL_COLORS = [
  '#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948',
];
const GRAY_RAMP_LIGHT: [number, number, number] = [216, 216, 214];
const GRAY_RAMP_DARK: [number, number, number] = [107, 106, 102];
const OTHERS_COLOR = '#5b5a56';

interface UserActivityRowDTO {
  rank: number;
  user: string;
  total: number;
  enabledInCharts: boolean;
  fieldCounts: Partial<Record<ActivityFieldKey, number>>;
}

type ActivityFieldKey = keyof Omit<UserActivityMonthStatsDTO, 'month'>;

interface ActivityFieldDef {
  key: ActivityFieldKey;
  group: string;
  label: string;
}

@Component({
  selector: 'app-toggle-in-charts-cell',
  template: `
    <div
      class="d-flex align-items-center justify-content-center h-100 w-100"
      style="cursor: pointer;"
      (click)="toggle()">
      <input type="checkbox" [checked]="params.value" style="pointer-events: none; transform: scale(1.5);" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class ToggleInChartsCellRenderer implements ICellRendererAngularComp {
  params!: ICellRendererParams<UserActivityRowDTO, boolean>;

  agInit(params: ICellRendererParams<UserActivityRowDTO, boolean>): void {
    this.params = params;
  }

  refresh(params: ICellRendererParams<UserActivityRowDTO, boolean>): boolean {
    this.params = params;
    return true;
  }

  toggle() {
    this.params.setValue?.(!this.params.value);
  }
}

const ACTIVITY_FIELDS: ActivityFieldDef[] = [
  { key: 'jiraIssueCreatedCount', group: 'Jira', label: 'Created' },
  { key: 'jiraIssueUpdatedCount', group: 'Jira', label: 'Updated' },
  { key: 'jiraIssueCommentedCount', group: 'Jira', label: 'Commented' },
  { key: 'jiraIssueClosedRejectedCount', group: 'Jira', label: 'Closed/Rejected' },
  { key: 'jiraIssueCloseResolvedCount', group: 'Jira', label: 'Closed/Resolved' },
  { key: 'githubPullRequestCreatedCount', group: 'GitHub', label: 'PR Created' },
  { key: 'githubPullRequestUpdatedCount', group: 'GitHub', label: 'PR Updated' },
  { key: 'githubPullRequestCommentedCount', group: 'GitHub', label: 'PR Commented' },
  { key: 'githubPullRequestMergedCount', group: 'GitHub', label: 'PR Merged' },
  { key: 'githubPullRequestClosedCount', group: 'GitHub', label: 'PR Closed' },
  { key: 'mailMessageSentCount', group: 'Mail', label: 'Sent' },
  { key: 'mailMessageRepliedCount', group: 'Mail', label: 'Replied' },
  { key: 'mailMessageVotedCount', group: 'Mail', label: 'Voted' },
];

@Component({
  selector: 'app-user-activity-chart',
  imports: [FormsModule, HighchartsChartComponent, AgGridAngular],
  templateUrl: './user-activity-chart-page.component.html',
})
export class UserActivityChartPage implements OnInit {

  fromYear = 2024;
  toYear = 2030;
  topN = 10;

  fieldCharts = signal<{ key: string; options: Highcharts.Options }[]>([]);

  rowData = signal<UserActivityRowDTO[]>([]);

  readonly fieldGroups: { group: string; fields: ActivityFieldDef[] }[] = ['Jira', 'GitHub', 'Mail'].map((group) => ({
    group,
    fields: ACTIVITY_FIELDS.filter((f) => f.group === group),
  }));

  colDefs: (ColDef<UserActivityRowDTO> | ColGroupDef<UserActivityRowDTO>)[] = [
    { field: 'rank', headerName: '#', width: 70 },
    { field: 'user', headerName: 'User', minWidth: 180, flex: 1 },
    {
      field: 'enabledInCharts',
      headerName: 'In Charts',
      width: 100,
      cellRenderer: ToggleInChartsCellRenderer,
    },
    { field: 'total', headerName: 'Total activity', width: 140 },
    ...this.fieldGroups.map((fieldGroup) => ({
      headerName: fieldGroup.group,
      children: [
        {
          colId: `group-total-${fieldGroup.group}`,
          headerName: 'Total',
          width: 100,
          valueGetter: (params: ValueGetterParams<UserActivityRowDTO>) =>
            fieldGroup.fields.reduce((sum, f) => sum + (params.data?.fieldCounts[f.key] ?? 0), 0),
        },
        ...fieldGroup.fields.map((field) => ({
          colId: field.key,
          headerName: field.label,
          width: 100,
          valueGetter: (params: ValueGetterParams<UserActivityRowDTO>) => params.data?.fieldCounts[field.key] ?? 0,
        })),
      ],
    })),
  ];

  enabledFields = signal<Set<ActivityFieldKey>>(new Set(ACTIVITY_FIELDS.map((f) => f.key)));

  // Per group (Jira/GitHub/Mail): whether any chart panel for the group is shown at all.
  visibleGroups = signal<Set<string>>(new Set(this.fieldGroups.map((g) => g.group)));

  // Per group (Jira/GitHub/Mail): expanded shows one panel per sub-category, collapsed shows a
  // single panel aggregating the group's enabled sub-categories.
  expandedGroups = signal<Set<string>>(
    new Set()
    // new Set(this.fieldGroups.map((g) => g.group))
  );

  private allStats: UserActivityStatsDTO[] = [];

  // Ranking (top N users, "others" bucket, month axis) is independent of which field panels are
  // shown, so it's computed once per search()/topN change and reused when toggling panels.
  private top: { user: string; total: number }[] = [];
  private topUsers = new Set<string>();
  // Which top users currently have their series shown on the charts (toggled via the grid).
  private chartEnabledUsers = new Set<string>();
  private othersCount = 0;
  private months: string[] = [];
  private byUser = new Map<string, UserActivityStatsDTO>();

  constructor(private userActivityStatsService: UserActivityStatsService) {}

  ngOnInit() {
    this.search();
  }

  search() {
    this.userActivityStatsService.queryUserActivityStats(this.fromYear, this.toYear).subscribe({
      next: (stats) => {
        this.allStats = stats;
        this.recomputeRanking();
      },
      error: (err) => {
        console.error('failed to load user activity stats', err);
      },
    });
  }

  onTopNChanged() {
    this.recomputeRanking();
  }

  isFieldEnabled(key: ActivityFieldKey): boolean {
    return this.enabledFields().has(key);
  }

  toggleField(key: ActivityFieldKey) {
    const next = new Set(this.enabledFields());
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    this.enabledFields.set(next);
    this.rebuildFieldCharts();
  }

  isGroupVisible(group: string): boolean {
    return this.visibleGroups().has(group);
  }

  toggleGroupVisible(group: string) {
    const next = new Set(this.visibleGroups());
    if (next.has(group)) {
      next.delete(group);
    } else {
      next.add(group);
    }
    this.visibleGroups.set(next);
    this.rebuildFieldCharts();
  }

  isGroupExpanded(group: string): boolean {
    return this.expandedGroups().has(group);
  }

  toggleGroupExpanded(group: string) {
    const next = new Set(this.expandedGroups());
    if (next.has(group)) {
      next.delete(group);
    } else {
      next.add(group);
    }
    this.expandedGroups.set(next);
    this.rebuildFieldCharts();
  }

  onChartToggleChanged(event: CellValueChangedEvent<UserActivityRowDTO>) {
    const user = event.data.user;
    if (event.newValue) {
      this.chartEnabledUsers.add(user);
    } else {
      this.chartEnabledUsers.delete(user);
    }
    this.rebuildFieldCharts();
  }

  private recomputeRanking() {
    const totals = this.allStats
      .map((dto) => ({ user: dto.user ?? 'unknown', total: totalOf(dto) }))
      .sort((a, b) => b.total - a.total);

    const topN = Math.max(1, this.topN || 1);
    this.top = totals.slice(0, topN);
    this.topUsers = new Set(this.top.map((t) => t.user));
    this.othersCount = totals.length - this.top.length;
    this.months = Array.from(new Set(this.allStats.flatMap((dto) => Object.keys(dto.perMonth ?? {})))).sort();
    this.byUser = new Map(this.allStats.map((dto) => [dto.user ?? 'unknown', dto]));
    this.chartEnabledUsers = new Set(this.topUsers);

    this.rowData.set(
      this.top.map((t, i) => {
        const dto = this.byUser.get(t.user);
        const fieldCounts: Partial<Record<ActivityFieldKey, number>> = {};
        for (const field of ACTIVITY_FIELDS) {
          fieldCounts[field.key] = dto?.[field.key] ?? 0;
        }
        return { rank: i + 1, user: t.user, total: t.total, enabledInCharts: true, fieldCounts };
      })
    );

    this.rebuildFieldCharts();
  }

  private rebuildFieldCharts() {
    const enabled = this.enabledFields();
    const specs: ChartPanelSpec[] = [];

    for (const fieldGroup of this.fieldGroups) {
      if (!this.isGroupVisible(fieldGroup.group)) {
        continue;
      }
      const groupEnabledFields = fieldGroup.fields.filter((f) => enabled.has(f.key));
      if (groupEnabledFields.length === 0) {
        continue;
      }
      if (this.isGroupExpanded(fieldGroup.group)) {
        for (const field of groupEnabledFields) {
          specs.push({
            key: field.key,
            title: `${field.group} – ${field.label}`,
            valueOf: (dto, month) => dto.perMonth?.[month]?.[field.key] ?? 0,
          });
        }
      } else {
        specs.push({
          key: fieldGroup.group,
          title: `${fieldGroup.group} – Total Activities`,
          valueOf: (dto, month) =>
            groupEnabledFields.reduce((sum, f) => sum + (dto.perMonth?.[month]?.[f.key] ?? 0), 0),
        });
      }
    }

    this.fieldCharts.set(specs.map((spec) => ({ key: spec.key, options: this.buildChartOptions(spec) })));
  }

  private buildChartOptions(spec: ChartPanelSpec): Highcharts.Options {
    const series: Highcharts.SeriesAreaOptions[] = this.top
      .map((t, rank) => ({ t, rank }))
      .filter(({ t }) => this.chartEnabledUsers.has(t.user))
      .map(({ t, rank }) => {
        const dto = this.byUser.get(t.user);
        return {
          type: 'area',
          id: `user-${t.user}`,
          name: t.user,
          color: colorForRank(rank, this.top.length),
          data: this.months.map((month) => [monthToUtc(month), dto ? spec.valueOf(dto, month) : 0]),
        };
      });

    if (this.othersCount > 0) {
      series.push({
        type: 'area',
        id: 'others',
        name: `Others (${this.othersCount} users)`,
        color: OTHERS_COLOR,
        dashStyle: 'Dash',
        data: this.months.map((month) => {
          let sum = 0;
          for (const dto of this.allStats) {
            if (!this.topUsers.has(dto.user ?? 'unknown')) {
              sum += spec.valueOf(dto, month);
            }
          }
          return [monthToUtc(month), sum];
        }),
      });
    }

    return {
      chart: { type: 'area', zooming: { type: 'x' } },
      title: { text: spec.title },
      subtitle: { text: `Top ${this.top.length} users${this.othersCount > 0 ? `, plus ${this.othersCount} others` : ''}` },
      xAxis: { type: 'datetime' },
      yAxis: { title: { text: 'Count' } },
      tooltip: { shared: false, valueDecimals: 0 },
      legend: { enabled: true, maxHeight: 100 },
      plotOptions: {
        area: {
          stacking: 'normal',
          lineWidth: 1,
          marker: { enabled: false },
        },
      },
      credits: { enabled: false },
      series,
    };
  }
}

interface ChartPanelSpec {
  key: string;
  title: string;
  valueOf: (dto: UserActivityStatsDTO, month: string) => number;
}

function totalOf(dto: UserActivityStatsDTO): number {
  let sum = 0;
  for (const field of ACTIVITY_FIELDS) {
    sum += dto[field.key] ?? 0;
  }
  return sum;
}

function monthToUtc(month: string): number {
  const [year, monthNum] = month.split('-').map((v) => parseInt(v, 10));
  return Date.UTC(year, (monthNum || 1) - 1, 1);
}

/** The first 8 ranks get the validated categorical palette; ranks beyond that recede into a
 * light-to-dark gray ramp instead of generating indistinguishable extra hues. */
function colorForRank(rank: number, topCount: number): string {
  if (rank < CATEGORICAL_COLORS.length) {
    return CATEGORICAL_COLORS[rank];
  }
  const tailLength = Math.max(1, topCount - CATEGORICAL_COLORS.length);
  const t = Math.min(1, (rank - CATEGORICAL_COLORS.length) / tailLength);
  const [r0, g0, b0] = GRAY_RAMP_LIGHT;
  const [r1, g1, b1] = GRAY_RAMP_DARK;
  const r = Math.round(r0 + (r1 - r0) * t);
  const g = Math.round(g0 + (g1 - g0) * t);
  const b = Math.round(b0 + (b1 - b0) * t);
  return `rgb(${r},${g},${b})`;
}
