import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  imports: [FormsModule],
  selector: 'app-number-range-filter',
  host: { class: 'd-flex gap-2 align-items-end' },
  template: `
    <div>
      <label class="form-label">{{ fromLabel }}</label>
      <input class="form-control" type="number" [style.width]="width" [min]="min" [max]="max" [ngModel]="from" (ngModelChange)="onFromChange($event)" />
    </div>
    <div>
      <label class="form-label">{{ toLabel }}</label>
      <input class="form-control" type="number" [style.width]="width" [min]="min" [max]="max" [ngModel]="to" (ngModelChange)="onToChange($event)" />
    </div>
  `,
})
export class NumberRangeFilter {
  @Input() fromLabel = 'From';
  @Input() toLabel = 'To';
  @Input() from: number | null = null;
  @Input() to: number | null = null;
  @Input() min: number | null = null;
  @Input() max: number | null = null;
  @Input() width: string | null = null;
  @Output() fromChange = new EventEmitter<number | null>();
  @Output() toChange = new EventEmitter<number | null>();

  onFromChange(value: number | null) {
    this.from = value;
    this.fromChange.emit(value);
  }

  onToChange(value: number | null) {
    this.to = value;
    this.toChange.emit(value);
  }
}
