import type * as Highcharts from 'highcharts';
import { ComponentTimelineDTO } from '../../rest';

/** Fixed-order categorical palette, same as the other chart pages. */
const CATEGORICAL_COLORS = [
  '#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948',
];

/** Builds the "issues created per time bucket" Highcharts options of 1 component: 1 series per issue type. */
export class IssuesCreatedTimelineBuilder {

  static build(component: ComponentTimelineDTO, bucketStarts: number[], bucketLabel: string,
               countOpenedBefore: boolean, creationOnly: boolean): Highcharts.Options {
    const series: Highcharts.SeriesOptionsType[] = (component.series ?? []).map((s, i) => ({
      type: 'line',
      name: s.issueType,
      data: bucketStarts.map((t, idx): [number, number] => [t, s.counts?.[idx] ?? 0]),
      color: CATEGORICAL_COLORS[i % CATEGORICAL_COLORS.length],
    }));

    return {
      chart: { type: 'line' },
      title: { text: component.component },
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
