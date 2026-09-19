import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HighchartsChartComponent } from 'highcharts-angular';
import type * as Highcharts from 'highcharts';
import { AgGridAngular } from 'ag-grid-angular';
import type { ICellRendererAngularComp } from 'ag-grid-angular';
import type { CellValueChangedEvent, ColDef, ICellRendererParams, ValueGetterParams } from 'ag-grid-community';
import { UserActivityStatsService } from '../rest';
import {
  GithubUserActivityStatsDTO,
  JiraUserActivityStatsDTO,
  MailMessageUserActivityStatsDTO,
} from '../rest';

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

interface FieldDef<TKey extends string> {
  key: TKey;
  label: string;
}

interface PanelRowDTO<TKey extends string> {
  rank: number;
  user: string;
  total: number;
  enabledInCharts: boolean;
  fieldCounts: Partial<Record<TKey, number>>;
}

interface StatsWithPerMonth {
  user?: string;
  perMonth?: { [month: string]: unknown };
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
  params!: ICellRendererParams<PanelRowDTO<string>, boolean>;

  agInit(params: ICellRendererParams<PanelRowDTO<string>, boolean>): void {
    this.params = params;
  }

  refresh(params: ICellRendererParams<PanelRowDTO<string>, boolean>): boolean {
    this.params = params;
    return true;
  }

  toggle() {
    this.params.setValue?.(!this.params.value);
  }
}

const JIRA_FIELDS: FieldDef<string>[] = [
  { key: 'jiraIssueCreatedCount', label: 'Created' },
  { key: 'jiraIssueUpdatedCount', label: 'Updated' },
  { key: 'jiraIssueCommentedCount', label: 'Commented' },
  { key: 'jiraIssueClosedRejectedCount', label: 'Closed/Rejected' },
  { key: 'jiraIssueCloseResolvedCount', label: 'Closed/Resolved' },
];

const GITHUB_FIELDS: FieldDef<string>[] = [
  { key: 'githubPullRequestCreatedCount', label: 'PR Created' },
  { key: 'githubPullRequestUpdatedCount', label: 'PR Updated' },
  { key: 'githubPullRequestCommentedCount', label: 'PR Commented' },
  { key: 'githubPullRequestMergedCount', label: 'PR Merged' },
  { key: 'githubPullRequestClosedCount', label: 'PR Closed' },
];

const MAIL_FIELDS: FieldDef<string>[] = [
  { key: 'mailMessageSentCount', label: 'Sent' },
  { key: 'mailMessageRepliedCount', label: 'Replied' },
  { key: 'mailMessageVotedCount', label: 'Voted' },
];

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

function monthToUtc(month: string): number {
  const [year, monthNum] = month.split('-').map((v) => parseInt(v, 10));
  return Date.UTC(year, (monthNum || 1) - 1, 1);
}

function fieldValue(obj: unknown, key: string): number {
  const value = (obj as Record<string, unknown> | undefined)?.[key];
  return typeof value === 'number' ? value : 0;
}

interface ChartPanelSpec {
  key: string;
  title: string;
  valueOf: (dto: StatsWithPerMonth, month: string) => number;
}

/**
 * Ranking, per-field breakdown grid, and per-month charts for one activity source (Jira, GitHub or
 * Mail). Kept generic and instantiated once per source because the 3 sources use unrelated user
 * identities (Jira reporter, GitHub login, mail "From" address) and unrelated field sets, so their
 * stats can't be merged into a single grid.
 */
class ActivityStatsPanel<TStats extends StatsWithPerMonth> {

  topN = 10;
  expanded = false;

  fieldCharts = signal<{ key: string; options: Highcharts.Options }[]>([]);
  rowData = signal<PanelRowDTO<string>[]>([]);
  colDefs: ColDef<PanelRowDTO<string>>[];
  enabledFields = signal<Set<string>>(new Set());

  private allStats: TStats[] = [];
  private top: { user: string; total: number }[] = [];
  private topUsers = new Set<string>();
  private chartEnabledUsers = new Set<string>();
  private othersCount = 0;
  private months: string[] = [];
  private byUser = new Map<string, TStats>();

  constructor(readonly title: string, readonly fields: FieldDef<string>[]) {
    this.enabledFields.set(new Set(fields.map((f) => f.key)));
    this.colDefs = [
      { field: 'rank', headerName: '#', width: 70 },
      { field: 'user', headerName: 'User', minWidth: 180, flex: 1 },
      {
        field: 'enabledInCharts',
        headerName: 'In Charts',
        width: 100,
        cellRenderer: ToggleInChartsCellRenderer,
      },
      { field: 'total', headerName: 'Total activity', width: 140 },
      ...fields.map((field) => ({
        colId: field.key,
        headerName: field.label,
        width: 120,
        valueGetter: (params: ValueGetterParams<PanelRowDTO<string>>) => params.data?.fieldCounts[field.key] ?? 0,
      })),
    ];
  }

  setStats(stats: TStats[]) {
    this.allStats = stats;
    this.recomputeRanking();
  }

  onTopNChanged() {
    this.recomputeRanking();
  }

  isFieldEnabled(key: string): boolean {
    return this.enabledFields().has(key);
  }

  toggleField(key: string) {
    const next = new Set(this.enabledFields());
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    this.enabledFields.set(next);
    this.rebuildFieldCharts();
  }

  toggleExpanded() {
    this.expanded = !this.expanded;
    this.rebuildFieldCharts();
  }

  onChartToggleChanged(event: CellValueChangedEvent<PanelRowDTO<string>>) {
    const user = event.data.user;
    if (event.newValue) {
      this.chartEnabledUsers.add(user);
    } else {
      this.chartEnabledUsers.delete(user);
    }
    this.rebuildFieldCharts();
  }

  private totalOf(dto: TStats): number {
    let sum = 0;
    for (const field of this.fields) {
      sum += fieldValue(dto, field.key);
    }
    return sum;
  }

  private recomputeRanking() {
    const totals = this.allStats
      .map((dto) => ({ user: dto.user ?? 'unknown', total: this.totalOf(dto) }))
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
        const fieldCounts: Partial<Record<string, number>> = {};
        for (const field of this.fields) {
          fieldCounts[field.key] = fieldValue(dto, field.key);
        }
        return { rank: i + 1, user: t.user, total: t.total, enabledInCharts: true, fieldCounts };
      })
    );

    this.rebuildFieldCharts();
  }

  private rebuildFieldCharts() {
    const enabled = this.enabledFields();
    const enabledFields = this.fields.filter((f) => enabled.has(f.key));
    if (enabledFields.length === 0) {
      this.fieldCharts.set([]);
      return;
    }

    const specs: ChartPanelSpec[] = this.expanded
      ? enabledFields.map((field) => ({
          key: field.key,
          title: `${this.title} – ${field.label}`,
          valueOf: (dto, month) => fieldValue(dto.perMonth?.[month], field.key),
        }))
      : [
          {
            key: this.title,
            title: `${this.title} – Total Activities`,
            valueOf: (dto, month) =>
              enabledFields.reduce((sum, f) => sum + fieldValue(dto.perMonth?.[month], f.key), 0),
          },
        ];

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

@Component({
  selector: 'app-user-activity-chart',
  imports: [FormsModule, HighchartsChartComponent, AgGridAngular],
  templateUrl: './user-activity-chart-page.component.html',
})
export class UserActivityChartPage implements OnInit {

  fromYear = 2024;
  toYear = 2030;

  readonly jiraPanel = new ActivityStatsPanel<JiraUserActivityStatsDTO>('Jira', JIRA_FIELDS);
  readonly githubPanel = new ActivityStatsPanel<GithubUserActivityStatsDTO>('GitHub', GITHUB_FIELDS);
  readonly mailPanel = new ActivityStatsPanel<MailMessageUserActivityStatsDTO>('Mail', MAIL_FIELDS);

  readonly panels: ActivityStatsPanel<StatsWithPerMonth>[] = [this.jiraPanel, this.githubPanel, this.mailPanel];

  constructor(private userActivityStatsService: UserActivityStatsService) {}

  ngOnInit() {
    this.search();
  }

  search() {
    this.userActivityStatsService.queryUserActivityStats(this.fromYear, this.toYear).subscribe({
      next: (result) => {
        this.jiraPanel.setStats(Object.values(result.jira ?? {}));
        this.githubPanel.setStats(Object.values(result.github ?? {}));
        this.mailPanel.setStats(Object.values(result.mail ?? {}));
      },
      error: (err) => {
        console.error('failed to load user activity stats', err);
      },
    });
  }
}
