import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  imports: [FormsModule],
  selector: 'app-date-range-filter',
  host: { class: 'd-flex gap-2 align-items-end' },
  template: `
    <div>
      <label class="form-label">{{ fromLabel }}</label>
      <input class="form-control" type="date" [ngModel]="from" (ngModelChange)="onFromChange($event)" />
    </div>
    <div>
      <label class="form-label">{{ toLabel }}</label>
      <input class="form-control" type="date" [ngModel]="to" (ngModelChange)="onToChange($event)" />
    </div>
  `,
})
export class DateRangeFilter {
  @Input() fromLabel = 'Updated from';
  @Input() toLabel = 'Updated to';
  @Input() from = '';
  @Input() to = '';
  @Output() fromChange = new EventEmitter<string>();
  @Output() toChange = new EventEmitter<string>();

  onFromChange(value: string) {
    this.from = value;
    this.fromChange.emit(value);
  }

  onToChange(value: string) {
    this.to = value;
    this.toChange.emit(value);
  }
}
