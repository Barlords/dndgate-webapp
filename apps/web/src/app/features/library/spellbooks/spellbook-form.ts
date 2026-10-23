import { ChangeDetectionStrategy, Component, input, OnInit, output } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ApiProblem } from '../../../core/errors/api-problem';
import { applyFieldErrors } from '../../../core/errors/form-errors';
import { Button } from '../../../shared/ui/button/button';
import { FormField } from '../../../shared/ui/form-field/form-field';

export const NAME_MAX = 255;
export const DESCRIPTION_MAX = 5000;

export interface SpellBookValue {
  name: string;
  description: string;
  ruleset: string;
}

export interface RulesetChoice {
  id: string;
  label: string;
}

/** A blank value (only spaces) counts as missing, as the API reads it. */
function notBlank(control: AbstractControl): ValidationErrors | null {
  return String(control.value ?? '').trim() === '' ? { required: true } : null;
}

/**
 * Name, description and (when creating) ruleset of a spell book. When editing, the ruleset is shown
 * but cannot change. It emits the trimmed values; the parent sends them and, if the API refuses,
 * hands the problem back through `showProblem`.
 */
@Component({
  selector: 'dg-spellbook-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, FormField, Button],
  styles: `
    textarea {
      min-height: 6rem;
    }

    .form__ruleset {
      margin: 0 0 var(--dg-space-3);
    }

    .form__actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-3);
    }
  `,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <dg-form-field label="Nom" [control]="form.controls.name">
        <input formControlName="name" type="text" />
      </dg-form-field>
      <dg-form-field label="Description" [control]="form.controls.description">
        <textarea formControlName="description" rows="4"></textarea>
      </dg-form-field>
      @if (rulesets().length > 0) {
        <dg-form-field label="Jeu de règles" [control]="form.controls.ruleset">
          <select formControlName="ruleset">
            @for (choice of rulesets(); track choice.id) {
              <option [value]="choice.id">{{ choice.label }}</option>
            }
          </select>
        </dg-form-field>
      }
      <div class="form__actions">
        <dg-button type="submit" [loading]="busy()">{{ submitLabel() }}</dg-button>
        <dg-button variant="ghost" (click)="cancelled.emit()">Annuler</dg-button>
      </div>
    </form>
  `,
})
export class SpellBookForm implements OnInit {
  /** The rulesets to choose from when creating; empty when editing (the ruleset is fixed). */
  readonly rulesets = input<RulesetChoice[]>([]);
  readonly initial = input<SpellBookValue>({ name: '', description: '', ruleset: '' });
  readonly submitLabel = input('Créer');
  readonly busy = input(false);
  readonly saved = output<SpellBookValue>();
  readonly cancelled = output<void>();

  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(NAME_MAX)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(DESCRIPTION_MAX)],
    }),
    ruleset: new FormControl('', { nonNullable: true }),
  });

  /** Fills the form once the inputs are known. */
  ngOnInit(): void {
    this.form.reset(this.initial());
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
    const { name, description, ruleset } = this.form.getRawValue();
    this.saved.emit({ name: name.trim(), description: description.trim(), ruleset });
  }
}
