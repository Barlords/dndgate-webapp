import { ChangeDetectionStrategy, Component, effect, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { map, startWith } from 'rxjs';
import { Button } from '../../../shared/ui/button/button';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { I18n } from '../../../core/i18n/i18n.service';

export const NOTE_MAX = 5000;
export const NOTE_FIRST_DATE = '1900-01-01';
export const NOTE_LAST_DATE = '2200-12-31';

export type NoteVisibility = 'PUBLIC' | 'PRIVATE';

/** What the editor hands over: the fields of a note, content trimmed. */
export interface NoteValue {
  /** `YYYY-MM-DD`. */
  date: string;
  content: string;
  visibility: NoteVisibility;
}

/** Today as `YYYY-MM-DD`, in the reader's time zone. */
function today(): string {
  const now = new Date();
  const two = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${two(now.getMonth() + 1)}-${two(now.getDate())}`;
}

function notBlank(control: AbstractControl): ValidationErrors | null {
  return String(control.value ?? '').trim() === '' ? { required: true } : null;
}

/** The API accepts dates from 1900 to 2200; a cleared date input is a missing date. */
function dateInRange(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '');
  if (value === '') {
    return { required: true };
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return { dateRange: true };
  }
  return value < NOTE_FIRST_DATE || value > NOTE_LAST_DATE ? { dateRange: true } : null;
}

/**
 * Fields of a note (date, content, and the visibility where it exists), for its creation and its
 * edition. It makes no HTTP call: it emits the values and the parent hands refusals back.
 */
@Component({
  selector: 'dg-note-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, FormField, Button],
  styles: `
    :host {
      display: block;
      padding: var(--dg-space-4);
      margin-bottom: var(--dg-space-4);
      background: var(--dg-vellum);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
    }

    textarea {
      min-height: 7rem;
    }

    .row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
      column-gap: var(--dg-space-4);
    }

    .counter {
      margin: calc(var(--dg-space-2) * -1) 0 var(--dg-space-3);
      font-size: var(--dg-text-sm);
      text-align: right;
      color: var(--dg-ink-muted);
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-2);
    }
  `,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="row">
        <dg-form-field [label]="t().tavern.notes.date" [control]="form.controls.date">
          <input formControlName="date" type="date" min="1900-01-01" max="2200-12-31" />
        </dg-form-field>
        @if (withVisibility()) {
          <dg-form-field [label]="t().tavern.notes.visibility" [control]="form.controls.visibility">
            <select formControlName="visibility">
              <option value="PUBLIC">{{ t().tavern.visibility['PUBLIC'] }}</option>
              <option value="PRIVATE">{{ t().tavern.visibility['PRIVATE'] }}</option>
            </select>
          </dg-form-field>
        }
      </div>
      <dg-form-field [label]="t().tavern.notes.note" [control]="form.controls.content">
        <textarea formControlName="content" rows="5"></textarea>
      </dg-form-field>
      <p class="counter">{{ length() }} / {{ max }}</p>
      <div class="actions">
        <dg-button type="submit" [loading]="busy()">{{
          submitLabel() ?? t().tavern.notes.write
        }}</dg-button>
        <dg-button variant="ghost" [disabled]="busy()" (click)="cancelled.emit()">{{
          t().common.actions.cancel
        }}</dg-button>
      </div>
    </form>
  `,
})
export class NoteEditor {
  protected readonly t = inject(I18n).t;

  /** The note to edit; without it, a blank note dated today. */
  readonly initial = input<NoteValue | null>(null);
  readonly submitLabel = input<string | null>(null);
  readonly busy = input(false);
  readonly withVisibility = input(false);
  readonly saved = output<NoteValue>();
  readonly cancelled = output<void>();

  protected readonly max = NOTE_MAX;

  protected readonly form = new FormGroup({
    date: new FormControl('', { nonNullable: true, validators: [dateInRange] }),
    content: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(NOTE_MAX)],
    }),
    visibility: new FormControl<NoteVisibility>('PUBLIC', { nonNullable: true }),
  });

  protected readonly length = toSignal(
    this.form.controls.content.valueChanges.pipe(
      startWith(''),
      map((value) => value.length),
    ),
    { initialValue: 0 },
  );

  constructor() {
    effect(() =>
      this.form.reset(this.initial() ?? { date: today(), content: '', visibility: 'PUBLIC' }),
    );
  }

  /** Puts a message the API's refusal calls for on the content field. */
  reject(message: string): void {
    this.form.controls.content.setErrors({ server: message });
    this.form.controls.content.markAsTouched();
  }

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saved.emit({
      date: value.date,
      content: value.content.trim(),
      visibility: value.visibility,
    });
  }
}
