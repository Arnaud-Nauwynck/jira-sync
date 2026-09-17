import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

/** Shows how many rows are available in the current fetch range versus overall, how many of the
 * currently-loaded rows match the criteria, and lets the user tune the server-fetch `limit`. */
@Component({
  imports: [FormsModule],
  selector: 'app-search-stats-bar',
  template: `
    <div class="small text-secondary mb-2 d-flex align-items-center flex-wrap gap-2">
      <span>
        Loaded: {{ loadedCount }}
        &nbsp;·&nbsp;
        in partitions [{{ rangeFrom }}, {{ rangeTo }}]: {{ availableInRange }}
        &nbsp;·&nbsp;
        Total: {{ total }}
      </span>
      <span class="ms-auto d-flex align-items-center gap-1">
        <label class="mb-0" for="limit">Limit</label>
        <input id="limit" class="form-control form-control-sm app-number-input-sm" type="number"
               [ngModel]="limit" (ngModelChange)="onLimitChange($event)"/>
      </span>
    </div>
  `,
})
export class SearchStatsBar {
  @Input() rangeFrom: string | number | undefined;
  @Input() rangeTo: string | number | undefined;
  @Input() availableInRange = 0;
  @Input() total = 0;
  @Input() loadedCount = 0;

  @Input() limit = 5000;
  @Output() limitChange = new EventEmitter<number>();

  onLimitChange(value: number) {
    this.limit = value;
    this.limitChange.emit(value);
  }
}
