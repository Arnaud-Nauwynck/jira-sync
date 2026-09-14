import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  imports: [FormsModule],
  selector: 'app-text-contains-filter',
  template: `
    <div>
      <label class="form-label">{{ label }}</label>
      <input class="form-control" type="text" [ngModel]="value" (ngModelChange)="onChange($event)" [placeholder]="placeholder" />
    </div>
  `,
})
export class TextContainsFilter {
  @Input() label = '';
  @Input() placeholder = 'comma separated';
  @Input() value = '';
  @Output() valueChange = new EventEmitter<string>();

  onChange(value: string) {
    this.value = value;
    this.valueChange.emit(value);
  }
}
