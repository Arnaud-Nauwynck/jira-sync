import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HighchartsChartComponent } from 'highcharts-angular';
import type * as Highcharts from 'highcharts';
import { GitHubPullRequestsService } from '../../rest';
import { GitHubPrDistributionEntryDTO, GitHubPrDistributionStatsDTO } from '../../rest';

/**
 * Fixed-order categorical palette (see the dataviz skill's references/palette.md): validated
 * colorblind-safe up to 8 series, matching the palette used by the user-activity-chart page.
 */
const CATEGORICAL_COLORS = [
  '#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948',
];
const OTHERS_COLOR = '#5b5a56';

interface DimensionOption {
  value: string;
  label: string;
}

const DIMENSION_OPTIONS: DimensionOption[] = [
  { value: 'STATE', label: 'State (open / merged / closed)' },
  { value: 'AUTHOR', label: 'Author' },
  { value: 'LABEL', label: 'Label' },
  { value: 'BASE_REF', label: 'Base branch' },
  { value: 'MERGEABLE_STATE', label: 'Mergeable state' },
];

/** Ranks beyond the top N are folded into a single "Other" slice instead of seating more colors. */
const MAX_SLICES = 8;

@Component({
  selector: 'app-pr-distribution-chart',
  imports: [FormsModule, HighchartsChartComponent],
  templateUrl: './pr-distribution-chart-page.component.html',
})
export class PrDistributionChartPage implements OnInit {

  readonly dimensionOptions = DIMENSION_OPTIONS;

  dimension = 'STATE';
  fromYear = 2012;
  toYear = 2030;
  topNUsers = 10;

  chartOptions = signal<Highcharts.Options | undefined>(undefined);
  userChartOptions = signal<Highcharts.Options | undefined>(undefined);
  total = signal<number>(0);
  loading = signal<boolean>(false);

  constructor(private gitHubPullRequestsService: GitHubPullRequestsService) {}

  ngOnInit() {
    this.search();
  }

  search() {
    this.loading.set(true);
    this.gitHubPullRequestsService
      .queryDistributionStats(this.dimension as any, this.fromYear, this.toYear, 'body', false, { httpHeaderAccept: 'application/json' as any })
      .subscribe({
        next: (stats) => {
          this.applyStats(stats);
          this.loading.set(false);
        },
        error: (err) => {
          console.error('failed to load PR distribution stats', err);
          this.loading.set(false);
        },
      });
  }

  private applyStats(stats: GitHubPrDistributionStatsDTO) {
    const entries = stats.entries ?? [];
    const total = entries.reduce((sum, e) => sum + (e.count ?? 0), 0);
    this.total.set(total);

    const dimensionLabel = this.dimensionOptions.find((d) => d.value === this.dimension)?.label;
    this.chartOptions.set(this.buildChartOptions('Pull Request Distribution', dimensionLabel, this.toSliceData(entries)));

    const entriesPerUser = stats.entriesPerUser ?? [];
    const topNUsers = Math.max(1, this.topNUsers || 1);
    this.userChartOptions.set(this.buildChartOptions('Pull Request Distribution per User', 'Author', this.toSliceData(entriesPerUser, topNUsers)));
  }

  private toSliceData(entries: GitHubPrDistributionEntryDTO[], maxSlices: number = MAX_SLICES): Highcharts.PointOptionsObject[] {
    const top = entries.slice(0, maxSlices);
    const others = entries.slice(maxSlices);
    const othersCount = others.reduce((sum, e) => sum + (e.count ?? 0), 0);

    const data: Highcharts.PointOptionsObject[] = top.map((e, i) => ({
      name: e.name,
      y: e.count,
      color: CATEGORICAL_COLORS[i],
    }));
    if (othersCount > 0) {
      data.push({ name: `Other (${others.length})`, y: othersCount, color: OTHERS_COLOR });
    }
    return data;
  }

  private buildChartOptions(title: string, subtitle: string | undefined, data: Highcharts.PointOptionsObject[]): Highcharts.Options {
    return {
      chart: { type: 'pie' },
      title: { text: title },
      subtitle: { text: subtitle },
      tooltip: { pointFormat: '{point.name}: <b>{point.y}</b> ({point.percentage:.1f}%)' },
      legend: { enabled: true },
      plotOptions: {
        pie: {
          allowPointSelect: true,
          showInLegend: true,
          borderRadius: 4,
          borderWidth: 2,
          dataLabels: {
            enabled: true,
            format: '{point.name}: {point.percentage:.1f}%',
          },
        },
      },
      credits: { enabled: false },
      series: [
        {
          type: 'pie',
          name: 'Pull Requests',
          data,
        },
      ],
    };
  }
}
