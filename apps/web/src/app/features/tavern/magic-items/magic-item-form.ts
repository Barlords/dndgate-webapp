import { ChangeDetectionStrategy, Component, effect, input, output } from '@angular/core';
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
import {
  MagicItemRarity,
  MagicItemType,
  RARITIES,
  RARITY_LABEL,
  TYPE_LABEL,
  TYPES,
} from './magic-item-labels';

export const NAME_MAX = 255;
export const DESCRIPTION_MAX = 5000;

export interface MagicItemValue {
  name: string;
  type: MagicItemType;
  rarity: MagicItemRarity;
  requiresAttunement: boolean;
  description: string;
}

export const BLANK_MAGIC_ITEM: MagicItemValue = {
  name: '',
  type: 'WONDROUS_ITEM',
  rarity: 'COMMON',
  requiresAttunement: false,
  description: '',
};

/** A blank value (only spaces) counts as missing, as the API reads it. */
function notBlank(control: AbstractControl): ValidationErrors | null {
  return String(control.value ?? '').trim() === '' ? { required: true } : null;
}

/**
 * Fields of a magic item, for its creation and its edition. It emits the trimmed values; the
 * parent sends them and, if the API refuses, hands the problem back through `showProblem`.
 */
@Component({
  selector: 'dg-magic-item-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, FormField, Button],
  styles: `
    textarea {
      min-height: 8rem;
    }

    .form__row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
      gap: 0 var(--dg-space-4);
    }

    .form__check {
      display: flex;
      align-items: center;
      gap: var(--dg-space-2);
      margin: 0 0 var(--dg-space-4);
      cursor: pointer;

      // A light box on the parchment, red once ticked, like the filters of the Library.
      input {
        width: 1.1rem;
        height: 1.1rem;
        color-scheme: light;
        accent-color: var(--dg-blood);
      }
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
      <div class="form__row">
        <dg-form-field label="Type" [control]="form.controls.type">
          <select formControlName="type">
            @for (type of types; track type) {
              <option [value]="type">{{ typeLabels[type] }}</option>
            }
          </select>
        </dg-form-field>
        <dg-form-field label="Rareté" [control]="form.controls.rarity">
          <select formControlName="rarity">
            @for (rarity of rarities; track rarity) {
              <option [value]="rarity">{{ rarityLabels[rarity] }}</option>
            }
          </select>
        </dg-form-field>
      </div>
      <label class="form__check">
        <input formControlName="requiresAttunement" type="checkbox" />
        Harmonisation requise
      </label>
      <dg-form-field label="Description" [control]="form.controls.description">
        <textarea formControlName="description" rows="6"></textarea>
      </dg-form-field>
      <div class="form__actions">
        <dg-button type="submit" [loading]="busy()">{{ submitLabel() }}</dg-button>
        <ng-content />
      </div>
    </form>
  `,
})
export class MagicItemForm {
  readonly initial = input<MagicItemValue>(BLANK_MAGIC_ITEM);
  readonly submitLabel = input('Créer l’objet');
  readonly busy = input(false);
  readonly saved = output<MagicItemValue>();

  protected readonly types = TYPES;
  protected readonly rarities = RARITIES;
  protected readonly typeLabels = TYPE_LABEL;
  protected readonly rarityLabels = RARITY_LABEL;

  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(NAME_MAX)],
    }),
    type: new FormControl<MagicItemType>(BLANK_MAGIC_ITEM.type, { nonNullable: true }),
    rarity: new FormControl<MagicItemRarity>(BLANK_MAGIC_ITEM.rarity, { nonNullable: true }),
    requiresAttunement: new FormControl(false, { nonNullable: true }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(DESCRIPTION_MAX)],
    }),
  });

  constructor() {
    // Filled from the item, and again when it is read afresh (after a concurrent change).
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
    this.saved.emit({ ...value, name: value.name.trim(), description: value.description.trim() });
  }
}
