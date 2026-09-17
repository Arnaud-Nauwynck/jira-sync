import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';

export interface ExcludeFilterOption {
  label: string;
  value: string;
}

/** Dropdown checklist of options, where checking an option means it is *included* (unchecking excludes it), with a "select/unselect all" shortcut. */
@Component({
  imports: [NgbDropdownModule],
  selector: 'app-exclude-dropdown-filter',
  template: `
    <div ngbDropdown autoClose="outside">
      <label class="form-label d-block">{{ label }}</label>
      <button class="btn btn-outline-secondary dropdown-toggle" type="button" ngbDropdownToggle>
        {{ label }}
        @if (excluded.size > 0) {
          <span class="badge text-bg-secondary">{{ excluded.size }} excluded</span>
        }
      </button>
      <div ngbDropdownMenu>
        <button
          ngbDropdownItem
          type="button"
          (click)="toggleAll()">
          {{ areAllSelected() ? 'Unselect all' : 'Select all' }}
        </button>
        <div class="dropdown-divider"></div>
        @for (option of options; track option.value) {
          <button
            ngbDropdownItem
            class="d-flex align-items-center gap-2"
            [class.text-decoration-line-through]="isExcluded(option.value)"
            [class.text-muted]="isExcluded(option.value)"
            type="button"
            (click)="toggle(option.value)">
            <input type="checkbox" [checked]="!isExcluded(option.value)" (click)="$event.preventDefault()" />
            {{ option.label }}
          </button>
        }
      </div>
    </div>
  `,
})
export class ExcludeDropdownFilter {
  @Input() label = '';
  @Input() options: ExcludeFilterOption[] = [];
  @Input() excluded = new Set<string>();
  @Output() excludedChange = new EventEmitter<Set<string>>();

  isExcluded(value: string): boolean {
    return this.excluded.has(value);
  }

  areAllSelected(): boolean {
    return this.excluded.size === 0;
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

  toggleAll() {
    const next = this.areAllSelected() ? new Set(this.options.map((o) => o.value)) : new Set<string>();
    this.excluded = next;
    this.excludedChange.emit(next);
  }
}
