import { ChangeDetectionStrategy, Component, effect, inject, input, output } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { AdventureCreationRequest } from '../../core/api/generated/models/adventure-creation-request';
import { ApiProblem } from '../../core/errors/api-problem';
import { applyFieldErrors } from '../../core/errors/form-errors';
import { Button } from '../../shared/ui/button/button';
import { FormField } from '../../shared/ui/form-field/form-field';
import { I18n } from '../../core/i18n/i18n.service';

export const NAME_MAX = 255;
export const DESCRIPTION_MAX = 5000;
export const PLACE_MAX = 255;
export const MAX_PLAYER_MIN = 1;
export const MAX_PLAYER_MAX = 100;

/** What the form hands over: the fields of an adventure, trimmed. */
export type AdventureValue = AdventureCreationRequest;

/** A blank value (only spaces) counts as missing, as the API reads it. */
function notBlank(control: AbstractControl): ValidationErrors | null {
  return String(control.value ?? '').trim() === '' ? { required: true } : null;
}

const BLANK: AdventureValue = {
  name: '',
  description: '',
  maxPlayer: 4,
  visibility: 'PUBLIC',
  postalCode: '',
  city: '',
};

/**
 * Fields of an adventure, for its creation and its edition. It makes no HTTP call: it emits the
 * trimmed values and, if the API refuses them, the parent hands the problem back to `showProblem`.
 */
@Component({
  selector: 'dg-adventure-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, FormField, Button],
  styles: `
    textarea {
      min-height: 8rem;
    }

    .row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
      column-gap: var(--dg-space-4);
    }
  `,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <dg-form-field [label]="t().tavern.adventureForm.name" [control]="form.controls.name">
        <input formControlName="name" type="text" />
      </dg-form-field>
      <dg-form-field
        [label]="t().tavern.adventureForm.description"
        [control]="form.controls.description"
      >
        <textarea formControlName="description" rows="6"></textarea>
      </dg-form-field>
      <div class="row">
        <dg-form-field
          [label]="t().tavern.adventureForm.maxPlayer"
          [control]="form.controls.maxPlayer"
        >
          <input formControlName="maxPlayer" type="number" min="1" max="100" />
        </dg-form-field>
        @if (withVisibility()) {
          <dg-form-field
            [label]="t().tavern.adventureForm.visibility"
            [control]="form.controls.visibility"
          >
            <select formControlName="visibility">
              <option value="PUBLIC">{{ t().tavern.visibility['PUBLIC'] }}</option>
              <option value="PRIVATE">{{ t().tavern.visibility['PRIVATE'] }}</option>
            </select>
          </dg-form-field>
        }
      </div>
      <div class="row">
        <dg-form-field
          [label]="t().tavern.adventureForm.postalCode"
          [control]="form.controls.postalCode"
        >
          <input formControlName="postalCode" type="text" autocomplete="postal-code" />
        </dg-form-field>
        <dg-form-field [label]="t().tavern.adventureForm.city" [control]="form.controls.city">
          <input formControlName="city" type="text" autocomplete="address-level2" />
        </dg-form-field>
      </div>
      <dg-button type="submit" [loading]="busy()">{{
        submitLabel() ?? t().common.actions.create
      }}</dg-button>
    </form>
  `,
})
export class AdventureForm {
  protected readonly t = inject(I18n).t;

  readonly initial = input<AdventureValue>(BLANK);
  readonly submitLabel = input<string | null>(null);
  readonly busy = input(false);
  /** False where the visibility has its own control, so that the form cannot contradict it. */
  readonly withVisibility = input(true);
  readonly saved = output<AdventureValue>();

  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(NAME_MAX)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(DESCRIPTION_MAX)],
    }),
    maxPlayer: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(MAX_PLAYER_MIN),
      Validators.max(MAX_PLAYER_MAX),
    ]),
    visibility: new FormControl<AdventureValue['visibility']>('PUBLIC', { nonNullable: true }),
    postalCode: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(PLACE_MAX)],
    }),
    city: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(PLACE_MAX)],
    }),
  });

  constructor() {
    // Every new `initial` replaces what is in the fields, edits included: the parent hands one over
    // when the adventure arrives after render, or is reloaded after a conflict.
    effect(() => this.form.reset(this.initial()));
  }

  /** Puts the API's per-field messages on the fields; false when none matched. */
  showProblem(problem: ApiProblem): boolean {
    return applyFieldErrors(this.form, problem);
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
      name: value.name.trim(),
      description: value.description.trim(),
      maxPlayer: value.maxPlayer as number,
      visibility: value.visibility,
      postalCode: value.postalCode.trim(),
      city: value.city.trim(),
    });
  }
}
