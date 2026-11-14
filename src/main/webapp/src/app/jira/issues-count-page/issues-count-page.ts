import {Component, computed, inject, signal} from '@angular/core';
import {JiraIssuesService} from '../../rest';
import {NgTemplateOutlet} from '@angular/common';
import {toSignal} from '@angular/core/rxjs-interop';

/** Dimensions of the server-side counts, in the same order as the server key array */
export type CountDimension = 'type' | 'status' | 'priority' | 'resolution' | 'component';

interface DimensionDef {
  id: CountDimension;
  label: string;
  /** position in the server key array */
  index: number;
}

export const COUNT_DIMENSIONS: readonly DimensionDef[] = [
  {id: 'type', label: 'IssueType', index: 0},
  {id: 'status', label: 'Status', index: 1},
  {id: 'priority', label: 'Priority', index: 2},
  {id: 'resolution', label: 'Resolution', index: 3},
  {id: 'component', label: 'ProjectComponent', index: 4},
];

export interface CountRow {
  /** values of the selected dimensions, in COUNT_DIMENSIONS order */
  values: string[];
  count: number;
}

/** one card per ProjectComponent, with the rows of the other selected dimensions */
export interface ComponentGroup {
  component: string;
  count: number;
  rows: CountRow[];
  /** e.g. "Bug / Major: 3 · Task / Minor: 1", empty when no other dimension is selected */
  text: string;
  /** per-priority breakdown (text / pie views), one group per combination of the other dimensions */
  priorityGroups: PriorityGroup[];
}

export type ComponentView = 'cards' | 'sections' | 'rows';

export type PriorityView = 'rows' | 'text' | 'pie';

export interface PieSlice {
  label: string;
  count: number;
  color: string;
}

/** one group per combination of the other selected dimensions, with the per-priority counts */
export interface PriorityGroup {
  values: string[];
  total: number;
  /** counts per priority, sorted by descending count */
  parts: {label: string; count: number}[];
  /** e.g. "5 Major + 2 Minor" */
  text: string;
  slices: PieSlice[];
  /** CSS conic-gradient drawing the pie */
  gradient: string;
}

const MIN_PIE_PX = 16;

const PIE_COLORS = ['#4e79a7', '#f28e2b', '#e15759', '#76b7b2', '#59a14f', '#edc948', '#b07aa1', '#ff9da7', '#9c755f', '#bab0ac'];

/** Jira priorities from most to least severe (lowercase keys) */
const PRIORITY_ORDER = ['blocker', 'critical', 'major', 'minor', 'trivial'];

/** priority colors: blocker red, critical orange, major yellow, minor blue, trivial light grey */
const PRIORITY_COLORS: Record<string, string> = {
  blocker: '#CC0000',
  critical: '#FF5630',
  major: '#FFC400',
  minor: '#4C9AFF',
  trivial: '#A5ADBA',
};

function priorityRank(label: string): number {
  const i = PRIORITY_ORDER.indexOf(label.toLowerCase());
  return i < 0 ? PRIORITY_ORDER.length : i;
}

function priorityColor(label: string): string {
  return PRIORITY_COLORS[label.toLowerCase()] ?? '#6c757d';
}

function comparePriority(a: string, b: string): number {
  return priorityRank(a) - priorityRank(b) || a.localeCompare(b);
}

@Component({
  imports: [NgTemplateOutlet],
  selector: 'app-issues-count-page',
  templateUrl: './issues-count-page.html',
})
export class IssuesBoardPage {

  readonly dimensions = COUNT_DIMENSIONS;

  private readonly jiraIssuesService = inject(JiraIssuesService);
  private readonly issuesCounts = toSignal(this.jiraIssuesService.queryIssuesCounts());

  readonly total = computed(() => this.issuesCounts()?.total ?? 0);
  readonly totalLive = computed(() => this.issuesCounts()?.totalLive ?? 0);

  /** selected group-by dimensions, kept in COUNT_DIMENSIONS order */
  readonly selectedDimensions = signal<CountDimension[]>(['priority', 'component']);

  /** one row per distinct combination of the selected dimensions, sorted by values */
  readonly rows = computed<CountRow[]>(() => {
    const selected = COUNT_DIMENSIONS.filter(d => this.selectedDimensions().includes(d.id));
    const byKey = new Map<string, CountRow>();
    for (const e of this.issuesCounts()?.countByKeys ?? []) {
      const count = e.count ?? 0;
      if (count === 0) {
        continue;
      }
      const values = selected.map(d => e.key?.[d.index] ?? `(none)`);
      const mapKey = values.join('\u0001');
      const row = byKey.get(mapKey);
      if (row) {
        row.count += count;
      } else {
        byKey.set(mapKey, {values, count});
      }
    }
    const priorityPos = selected.findIndex(d => d.id === 'priority');
    return [...byKey.values()].sort((a, b) => compareValues(a.values, b.values, priorityPos));
  });

  /** sum of the displayed rows; differs from total when ProjectComponent is not selected and issues have several components */
  readonly rowsTotal = computed(() => this.rows().reduce((s, r) => s + r.count, 0));

  /** labels of the selected dimensions other than ProjectComponent, used as headers in component cards */
  readonly otherSelectedLabels = computed<string[]>(() =>
    COUNT_DIMENSIONS.filter(d => d.id !== 'component' && this.selectedDimensions().includes(d.id)).map(d => d.label));

  /** one group per ProjectComponent, sorted by descending issues count (then name) */
  readonly componentGroups = computed<ComponentGroup[]>(() => {
    const selected = COUNT_DIMENSIONS.filter(d => this.selectedDimensions().includes(d.id));
    const componentPos = selected.findIndex(d => d.id === 'component');
    if (componentPos < 0) {
      return [];
    }
    const hasOtherDimensions = selected.length > 1;
    const priorityPosInRows = selected.filter(d => d.id !== 'component').findIndex(d => d.id === 'priority');
    const byComponent = new Map<string, {component: string; count: number; rows: CountRow[]}>();
    for (const row of this.rows()) {
      const component = row.values[componentPos];
      let group = byComponent.get(component);
      if (!group) {
        group = {component, count: 0, rows: []};
        byComponent.set(component, group);
      }
      group.count += row.count;
      group.rows.push({values: row.values.filter((_, i) => i !== componentPos), count: row.count});
    }
    return [...byComponent.values()]
      .map(g => ({
        ...g,
        text: hasOtherDimensions ? g.rows.map(r => `${r.values.join(' / ')}: ${r.count}`).join(' · ') : '',
        priorityGroups: priorityPosInRows < 0 ? [] : groupByPriority(g.rows, priorityPosInRows),
      }))
      .sort((a, b) => b.count - a.count || a.component.localeCompare(b.component));
  });

  readonly componentViewOptions: readonly {id: ComponentView; label: string}[] = [
    {id: 'cards', label: 'Cards'},
    {id: 'sections', label: 'Titled sections'},
    {id: 'rows', label: 'One component per row'},
  ];
  readonly componentView = signal<ComponentView>('cards');
  readonly componentMenuOpen = signal(false);

  readonly componentViewLabel = computed(() =>
    this.componentViewOptions.find(o => o.id === this.componentView())?.label ?? '');

  /** from the ProjectComponent menu: null turns the dimension off, otherwise turns it on with the given view */
  selectComponentView(view: ComponentView | null) {
    if (view !== null) {
      this.componentView.set(view);
    }
    this.setDimensionSelected('component', view !== null);
    this.componentMenuOpen.set(false);
  }

  readonly priorityViewOptions: readonly {id: PriorityView; label: string}[] = [
    {id: 'rows', label: '1 row per priority'},
    {id: 'text', label: 'Text on one row'},
    {id: 'pie', label: 'Pie chart'},
  ];
  readonly priorityView = signal<PriorityView>('pie');

  /** labels of the selected dimensions other than Priority */
  readonly priorityOtherLabels = computed<string[]>(() =>
    COUNT_DIMENSIONS.filter(d => d.id !== 'priority' && this.selectedDimensions().includes(d.id)).map(d => d.label));

  /** one group per combination of the other selected dimensions, with the counts per priority */
  readonly priorityGroups = computed<PriorityGroup[]>(() => {
    const selected = COUNT_DIMENSIONS.filter(d => this.selectedDimensions().includes(d.id));
    const priorityPos = selected.findIndex(d => d.id === 'priority');
    return priorityPos < 0 ? [] : groupByPriority(this.rows(), priorityPos);
  });

  /** true when Priority is shown as text or pie instead of one row per priority */
  readonly usePriorityDetail = computed(() => this.isSelected('priority') && this.priorityView() !== 'rows');

  readonly groupByButtons: readonly {id: CountDimension; text: string}[] = [
    {id: 'type', text: 'per Type'},
    {id: 'status', text: 'per Status'},
    {id: 'priority', text: 'per Priority'},
    {id: 'resolution', text: 'per Resolution'},
    {id: 'component', text: 'per ProjectComponent'},
  ];

  readonly priorityMenuOpen = signal(false);

  readonly priorityViewLabel = computed(() =>
    this.priorityViewOptions.find(o => o.id === this.priorityView())?.label ?? '');

  /** from the Priority menu: null turns the dimension off, otherwise turns it on with the given view */
  selectPriorityView(view: PriorityView | null) {
    if (view !== null) {
      this.priorityView.set(view);
    }
    this.setDimensionSelected('priority', view !== null);
    this.priorityMenuOpen.set(false);
  }

  toggleDimension(dimension: CountDimension) {
    this.setDimensionSelected(dimension, !this.isSelected(dimension));
  }

  private setDimensionSelected(dimension: CountDimension, selected: boolean) {
    const others = this.selectedDimensions().filter(d => d !== dimension);
    const next = selected ? [...others, dimension] : others;
    this.selectedDimensions.set(COUNT_DIMENSIONS.map(d => d.id).filter(id => next.includes(id)));
  }

  /** pie diameter in px: the pie area is proportional to count / totalLive, for a full pie of maxPx */
  pieSize(count: number, maxPx: number): number {
    const live = this.totalLive();
    if (live <= 0) {
      return maxPx;
    }
    return Math.max(MIN_PIE_PX, Math.round(maxPx * Math.sqrt(Math.min(count / live, 1))));
  }

  isSelected(dimension: CountDimension): boolean {
    return this.selectedDimensions().includes(dimension);
  }

  /** text color of a cell when its column (in selected-dimension order) is Priority, otherwise null */
  cellColorAt(columnIndex: number, value: string): string | null {
    return this.selectedDimensions()[columnIndex] === 'priority' ? priorityColor(value) : null;
  }
}

/** one group per combination of the values other than priority, with the counts per priority */
function groupByPriority(rows: CountRow[], priorityPos: number): PriorityGroup[] {
  const byOther = new Map<string, {values: string[]; total: number; parts: {label: string; count: number}[]}>();
  for (const row of rows) {
    const values = row.values.filter((_, i) => i !== priorityPos);
    const key = values.join('\u0001');
    let group = byOther.get(key);
    if (!group) {
      group = {values, total: 0, parts: []};
      byOther.set(key, group);
    }
    group.total += row.count;
    group.parts.push({label: row.values[priorityPos], count: row.count});
  }
  return [...byOther.values()].map(g => {
    const parts = [...g.parts].sort((a, b) => comparePriority(a.label, b.label));
    const slices = parts.map(p => ({label: p.label, count: p.count, color: priorityColor(p.label)}));
    return {
      values: g.values,
      total: g.total,
      parts,
      text: parts.map(p => `${p.count} ${p.label}`).join(' + '),
      slices,
      gradient: buildPieGradient(slices, g.total),
    };
  });
}

/** CSS conic-gradient drawing the slices clockwise from 12 o'clock */
function buildPieGradient(slices: PieSlice[], total: number): string {
  let from = 0;
  const stops = slices.map(s => {
    const to = from + (total > 0 ? (s.count / total) * 100 : 0);
    const stop = `${s.color} ${from}% ${to}%`;
    from = to;
    return stop;
  });
  return `conic-gradient(${stops.join(', ')})`;
}

function compareValues(a: string[], b: string[], priorityPos: number): number {
  for (let i = 0; i < a.length; i++) {
    const c = i === priorityPos ? comparePriority(a[i], b[i]) : a[i].localeCompare(b[i]);
    if (c !== 0) {
      return c;
    }
  }
  return 0;
}
