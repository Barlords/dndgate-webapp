import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  signal,
  ViewEncapsulation,
} from '@angular/core';
import { AbstractControl, ValidationErrors } from '@angular/forms';
import { Dictionary } from '../../../core/i18n/dictionary';
import { I18n } from '../../../core/i18n/i18n.service';

let nextId = 0;

/** The message of one validation error, in the reader's language; `server` carries the API's. */
function describe(key: string, error: unknown, t: Dictionary['common']['validation']): string {
  const details = (error ?? {}) as Record<string, unknown>;
  switch (key) {
    case 'required':
      return t.required;
    case 'minlength':
      return t.minLength(Number(details['requiredLength']));
    case 'maxlength':
      return t.maxLength(Number(details['requiredLength']));
    case 'min':
      return t.min(Number(details['min']));
    case 'max':
      return t.max(Number(details['max']));
    case 'dateRange':
      return t.dateRange;
    case 'maxbytes':
      return t.maxBytes(Number(details['max']));
    case 'mismatch':
      return t.mismatch;
    case 'pattern':
      return t.pattern;
    case 'email':
      return t.email;
    case 'server':
      return typeof error === 'string' ? error : t.invalid;
    default:
      return t.invalid;
  }
}

/**
 * Labelled field: wraps the projected input, select or textarea and lists the messages of
 * `control.errors` under it. Local errors appear once the field is touched or edited; a `server`
 * error (set after a refused submit) appears at once.
 */
@Component({
  selector: 'dg-form-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Not encapsulated: the projected fields are styled here, under the dg-form-field selector.
  encapsulation: ViewEncapsulation.None,
  template: `
    <!-- eslint-disable-next-line @angular-eslint/template/label-has-associated-control -- the field is projected into the label -->
    <label class="dg-form-field__label">
      <span class="dg-form-field__caption">{{ label() }}</span>
      <ng-content />
    </label>
    <div class="dg-form-field__errors" [id]="errorsId" aria-live="polite">
      @for (message of messages(); track $index) {
        <p class="dg-form-field__error">{{ message }}</p>
      }
    </div>
  `,
  styleUrl: './form-field.scss',
})
export class FormField {
  private readonly t = inject(I18n).t;

  readonly label = input.required<string>();
  readonly control = input.required<AbstractControl>();

  protected readonly errorsId = `dg-form-field-errors-${nextId++}`;

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  /** Bumped on every event of the control, which is not a signal itself. */
  private readonly changes = signal(0);

  protected readonly messages = computed(() => {
    this.changes();
    const control = this.control();
    const errors: ValidationErrors | null = control.errors;
    if (!errors) {
      return [];
    }
    const local = control.touched || control.dirty;
    return Object.entries(errors)
      .filter(([key]) => local || key === 'server')
      .map(([key, error]) => describe(key, error, this.t().common.validation));
  });

  constructor() {
    effect((onCleanup) => {
      const subscription = this.control().events.subscribe(() => this.changes.update((n) => n + 1));
      onCleanup(() => subscription.unsubscribe());
    });

    afterRenderEffect(() => {
      const shown = this.messages().length > 0;
      const field = this.host.nativeElement.querySelector('input, select, textarea');
      if (!field) {
        return;
      }
      if (shown) {
        field.setAttribute('aria-invalid', 'true');
        field.setAttribute('aria-describedby', this.errorsId);
      } else {
        field.removeAttribute('aria-invalid');
        field.removeAttribute('aria-describedby');
      }
    });
  }
}
