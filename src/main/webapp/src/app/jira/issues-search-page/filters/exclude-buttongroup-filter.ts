import { Component, EventEmitter, Input, Output } from '@angular/core';

/** Button group of options, where clicking a button toggles it excluded (struck-through) vs included. */
@Component({
  selector: 'app-exclude-buttongroup-filter',
  template: `
    <div>
      <label class="form-label d-block">{{ label }}</label>
      <div class="btn-group" role="group">
        @for (option of options; track option) {
          <button
            class="btn"
            [class.btn-outline-secondary]="!isExcluded(option)"
            [class.btn-secondary]="isExcluded(option)"
            [class.text-decoration-line-through]="isExcluded(option)"
            type="button"
            (click)="toggle(option)">{{ option }}</button>
        }
      </div>
    </div>
  `,
})
export class ExcludeButtonGroupFilter {
  @Input() label = '';
  @Input() options: string[] = [];
  @Input() excluded = new Set<string>();
  @Output() excludedChange = new EventEmitter<Set<string>>();

  isExcluded(value: string): boolean {
    return this.excluded.has(value);
  }

  toggle(value: string) {
    const next = new Set(this.excluded);
    if (next.has(value)) {
      next.delete(value);
    } else {
      next.add(value);
    }
    this.excluded = next;
    this.excludedChange.emit(next);
  }
}
