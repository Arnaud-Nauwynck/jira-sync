import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

/** Tri-state availability filter: 'no' = not present, 'yes' = present, 'any' = no filtering. */
export type AvailabilityFilter = 'no' | 'any' | 'yes';

@Component({
  imports: [FormsModule],
  selector: 'app-availability-filter',
  template: `
    <div>
      <label class="form-label d-block">{{ label }}</label>
      <div class="btn-group" role="group">
        <input type="radio" class="btn-check" [name]="name" [id]="name + 'No'" autocomplete="off"
          [ngModel]="value" (ngModelChange)="onChange($event)" value="no" />
        <label class="btn btn-outline-secondary" [for]="name + 'No'">✗</label>

        <input type="radio" class="btn-check" [name]="name" [id]="name + 'Any'" autocomplete="off"
          [ngModel]="value" (ngModelChange)="onChange($event)" value="any" />
        <label class="btn btn-outline-secondary" [for]="name + 'Any'">Any</label>

        <input type="radio" class="btn-check" [name]="name" [id]="name + 'Yes'" autocomplete="off"
          [ngModel]="value" (ngModelChange)="onChange($event)" value="yes" />
        <label class="btn btn-outline-secondary" [for]="name + 'Yes'">✓</label>
      </div>
    </div>
  `,
})
export class AvailabilityFilterComponent {
  @Input() label = 'Available';
  @Input() name = '';
  @Input() value: AvailabilityFilter = 'any';
  @Output() valueChange = new EventEmitter<AvailabilityFilter>();

  onChange(value: AvailabilityFilter) {
    this.value = value;
    this.valueChange.emit(value);
  }
}
