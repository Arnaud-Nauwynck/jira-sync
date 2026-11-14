import type * as Highcharts from 'highcharts';
import { ComponentTimelineDTO, IssuesTimelineDTO } from '../../rest';

/** Fixed-order categorical palette, same as the other chart pages. */
const CATEGORICAL_COLORS = [
  '#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948',
];

/** Builds the "issues created per time bucket" Highcharts options: 1 series per issue type of 1 component, or 1 total series. */
export class IssuesCreatedTimelineBuilder {

  /** Highcharts options of 1 component: 1 series per issue type. */
  static build(component: ComponentTimelineDTO, bucketStarts: number[], bucketLabel: string,
               countOpenedBefore: boolean, creationOnly: boolean): Highcharts.Options {
    const series: Highcharts.SeriesOptionsType[] = (component.series ?? []).map((s, i) => ({
      type: 'line',
      name: s.issueType,
      data: bucketStarts.map((t, idx): [number, number] => [t, s.counts?.[idx] ?? 0]),
      color: CATEGORICAL_COLORS[i % CATEGORICAL_COLORS.length],
    }));
    return IssuesCreatedTimelineBuilder.chartOptions(component.component ?? '', series, bucketLabel, countOpenedBefore, creationOnly);
  }

  /** Highcharts options of all components and issue types summed into 1 single total series. */
  static buildTotal(timeline: IssuesTimelineDTO, bucketLabel: string,
                    countOpenedBefore: boolean, creationOnly: boolean): Highcharts.Options {
    const bucketStarts = timeline.bucketStarts ?? [];
    const totals = bucketStarts.map(() => 0);
    for (const component of timeline.components ?? []) {
      for (const s of component.series ?? []) {
        (s.counts ?? []).forEach((count, idx) => {
          if (idx < totals.length) {
            totals[idx] += count;
          }
        });
      }
    }
    const series: Highcharts.SeriesOptionsType[] = [{
      type: 'line',
      name: 'Total',
      data: bucketStarts.map((t, idx): [number, number] => [t, totals[idx]]),
      color: CATEGORICAL_COLORS[0],
    }];
    return IssuesCreatedTimelineBuilder.chartOptions('All components, all issue types', series, bucketLabel, countOpenedBefore, creationOnly);
  }

  /** Highcharts options with 1 line series per component, each summing all its issue types. */
  static buildPerComponent(timeline: IssuesTimelineDTO, bucketLabel: string,
                           countOpenedBefore: boolean, creationOnly: boolean): Highcharts.Options {
    const bucketStarts = timeline.bucketStarts ?? [];
    const series: Highcharts.SeriesOptionsType[] = (timeline.components ?? []).map((c, i) => ({
      type: 'line',
      name: c.component ?? '',
      data: IssuesCreatedTimelineBuilder.toPoints(bucketStarts, IssuesCreatedTimelineBuilder.componentTotals(c, bucketStarts)),
      color: CATEGORICAL_COLORS[i % CATEGORICAL_COLORS.length],
    }));
    return IssuesCreatedTimelineBuilder.chartOptions('Per component', series, bucketLabel, countOpenedBefore, creationOnly);
  }

  /** Highcharts options with 1 line series per issue type, each summing its counts over all components. */
  static buildPerIssueType(timeline: IssuesTimelineDTO, bucketLabel: string,
                           countOpenedBefore: boolean, creationOnly: boolean): Highcharts.Options {
    const bucketStarts = timeline.bucketStarts ?? [];
    const totalsByIssueType = new Map<string, number[]>();
    for (const component of timeline.components ?? []) {
      for (const s of component.series ?? []) {
        const issueType = s.issueType ?? '';
        const totals = totalsByIssueType.get(issueType) ?? bucketStarts.map(() => 0);
        IssuesCreatedTimelineBuilder.addCounts(totals, s.counts ?? []);
        totalsByIssueType.set(issueType, totals);
      }
    }
    const series: Highcharts.SeriesOptionsType[] = [...totalsByIssueType].map(([issueType, totals], i) => ({
      type: 'line',
      name: issueType,
      data: IssuesCreatedTimelineBuilder.toPoints(bucketStarts, totals),
      color: CATEGORICAL_COLORS[i % CATEGORICAL_COLORS.length],
    }));
    return IssuesCreatedTimelineBuilder.chartOptions('Per issue type', series, bucketLabel, countOpenedBefore, creationOnly);
  }

  private static componentTotals(component: ComponentTimelineDTO, bucketStarts: number[]): number[] {
    const totals = bucketStarts.map(() => 0);
    for (const s of component.series ?? []) {
      IssuesCreatedTimelineBuilder.addCounts(totals, s.counts ?? []);
    }
    return totals;
  }

  private static addCounts(totals: number[], counts: number[]): void {
    counts.forEach((count, idx) => {
      if (idx < totals.length) {
        totals[idx] += count;
      }
    });
  }

  private static toPoints(bucketStarts: number[], values: number[]): [number, number][] {
    return bucketStarts.map((t, idx): [number, number] => [t, values[idx] ?? 0]);
  }

  private static chartOptions(title: string, series: Highcharts.SeriesOptionsType[], bucketLabel: string,
                              countOpenedBefore: boolean, creationOnly: boolean): Highcharts.Options {
    return {
      chart: { type: 'line' },
      title: { text: title },
      plotOptions: { line: { marker: { enabled: false } } },
      xAxis: { type: 'datetime' },
      yAxis: {
        title: { text: IssuesCreatedTimelineBuilder.yAxisTitle(bucketLabel, countOpenedBefore, creationOnly) },
        min: 0, allowDecimals: false,
      },
      tooltip: { shared: true, xDateFormat: '%Y-%m-%d' },
      legend: { enabled: true },
      credits: { enabled: false },
      series,
    };
  }

  private static yAxisTitle(bucketLabel: string, countOpenedBefore: boolean, creationOnly: boolean): string {
    if (!creationOnly) {
      return 'Issues still open at the end of each ' + bucketLabel;
    }
    return countOpenedBefore ? 'Issues created up to each ' + bucketLabel : 'Issues created per ' + bucketLabel;
  }
}
