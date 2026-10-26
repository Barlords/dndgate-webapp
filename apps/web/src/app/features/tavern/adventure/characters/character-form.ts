import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
} from '@angular/forms';
import { startWith } from 'rxjs';
import { ApiProblem } from '../../../../core/errors/api-problem';
import { applyFieldErrors } from '../../../../core/errors/form-errors';
import { PlanHint } from '../../../../shared/ui/plan-hint/plan-hint';
import { Button } from '../../../../shared/ui/button/button';
import { FormField } from '../../../../shared/ui/form-field/form-field';
import { Dictionary } from '../../../../core/i18n/dictionary';
import { I18n } from '../../../../core/i18n/i18n.service';

export const NAME_MIN = 3;
export const NAME_MAX = 50;
export const RACE_MIN = 3;
export const RACE_MAX = 255;
export const CLASS_MAX = 255;
export const PERSONALITY_MAX = 5000;
export const BACKGROUND_MAX = 80_000;
export const PICTURE_MAX_BYTES = 5 * 1024 * 1024;

/** The text fields of a character. */
export interface CharacterValue {
  name: string;
  race: string;
  martialClass: string;
  personality: string;
  background: string;
}

/** What the form hands over: trimmed fields and, when it asks for one, the checked picture. */
export interface CharacterSubmission {
  value: CharacterValue;
  picture: File | null;
}

const BLANK: CharacterValue = {
  name: '',
  race: '',
  martialClass: '',
  personality: '',
  background: '',
};

/** Length rule on the trimmed text, as the API reads it: blank is missing. */
function trimmedLength(min: number, max: number): ValidatorFn {
  return (control): ValidationErrors | null => {
    const length = String(control.value ?? '').trim().length;
    if (length === 0) {
      return { required: true };
    }
    if (length < min) {
      return { minlength: { requiredLength: min, actualLength: length } };
    }
    return length > max ? { maxlength: { requiredLength: max, actualLength: length } } : null;
  };
}

function startsWith(bytes: Uint8Array, signature: number[]): boolean {
  return signature.every((byte, index) => bytes[index] === byte);
}

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47];
const JPEG_SIGNATURE = [0xff, 0xd8, 0xff];

/** The words of the picture's problems, in the reader's language. */
export type PictureTexts = Pick<
  Dictionary['tavern']['characters'],
  'pictureTooBig' | 'pictureFormat'
>;

/**
 * Checks a picture before it is sent: at most 5 MB, and PNG or JPEG by its first bytes (the name
 * and the declared type prove nothing). Returns the message to show, or null when it is fine.
 */
export async function checkPicture(file: File, texts: PictureTexts): Promise<string | null> {
  if (file.size > PICTURE_MAX_BYTES) {
    return texts.pictureTooBig;
  }
  const head = new Uint8Array(await file.slice(0, PNG_SIGNATURE.length + 4).arrayBuffer());
  return startsWith(head, PNG_SIGNATURE) || startsWith(head, JPEG_SIGNATURE)
    ? null
    : texts.pictureFormat;
}

/**
 * Fields of a character, for its creation (with its picture) and its edition. It makes no HTTP
 * call: it emits the trimmed values and, if the API refuses them, the parent hands the problem back
 * to `showProblem`.
 */
@Component({
  selector: 'dg-character-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, FormField, Button, PlanHint],
  styles: `
    textarea {
      min-height: 8rem;
    }

    .counter {
      margin: calc(-1 * var(--dg-space-3)) 0 var(--dg-space-4);
      font-size: var(--dg-text-sm);
      text-align: right;
      color: var(--dg-ink-muted);
    }

    .counter.over {
      color: var(--dg-blood);
      font-weight: 600;
    }

    .picture {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      gap: var(--dg-space-4);
      margin: 0 0 var(--dg-space-4);
    }

    .caption {
      display: block;
      margin-bottom: var(--dg-space-1);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      color: var(--dg-ink-muted);
    }

    .preview {
      display: block;
      width: 9rem;
      aspect-ratio: 3 / 4;
      object-fit: cover;
      border: 3px solid var(--dg-gold-deep);
      border-radius: var(--dg-radius);
    }

    .note,
    .picture-hint {
      margin: var(--dg-space-1) 0 var(--dg-space-4);
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }

    .problem {
      margin: var(--dg-space-1) 0 0;
      color: var(--dg-blood);
      font-weight: 600;
    }
  `,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      @if (withPicture() && !pictureAllowed()) {
        <dg-plan-hint
          class="picture-hint"
          feature="characterPictures"
          [scope]="hintScope()"
          [currentPlanId]="currentPlanId()"
        />
      } @else if (withPicture()) {
        <div class="picture">
          <div>
            <label class="caption" for="dg-character-picture">{{
              t().tavern.characters.picture
            }}</label>
            <input
              id="dg-character-picture"
              type="file"
              accept="image/png,image/jpeg"
              (change)="pick($event)"
            />
            <p class="note">{{ t().tavern.characters.pictureOptional }}</p>
            @if (pictureProblem(); as message) {
              <p class="problem" role="alert">{{ message }}</p>
            }
          </div>
          @if (previewUrl(); as url) {
            <img class="preview" [src]="url" [alt]="t().tavern.characters.preview" />
          }
        </div>
      }
      <dg-form-field [label]="t().tavern.characters.name" [control]="form.controls.name">
        <input formControlName="name" type="text" autocomplete="off" />
      </dg-form-field>
      <dg-form-field [label]="t().tavern.characters.race" [control]="form.controls.race">
        <input formControlName="race" type="text" autocomplete="off" />
      </dg-form-field>
      <dg-form-field [label]="t().tavern.characters.class" [control]="form.controls.martialClass">
        <input formControlName="martialClass" type="text" autocomplete="off" />
      </dg-form-field>
      <dg-form-field
        [label]="t().tavern.characters.personality"
        [control]="form.controls.personality"
      >
        <textarea formControlName="personality" rows="4"></textarea>
      </dg-form-field>
      <dg-form-field
        [label]="t().tavern.characters.background"
        [control]="form.controls.background"
      >
        <textarea formControlName="background" rows="8"></textarea>
      </dg-form-field>
      <p class="counter" [class.over]="backgroundLength() > backgroundMax">
        {{ formatted(backgroundLength()) }} / {{ formatted(backgroundMax) }}
      </p>
      <dg-button type="submit" [loading]="busy()">{{
        submitLabel() ?? t().tavern.characters.createSubmit
      }}</dg-button>
    </form>
  `,
})
export class CharacterForm {
  protected readonly t = inject(I18n).t;

  readonly initial = input<CharacterValue>(BLANK);
  readonly submitLabel = input<string | null>(null);
  readonly busy = input(false);
  /** Creation offers a picture (optional); the API cannot change it afterwards. */
  readonly withPicture = input(false);
  /** False when the reader's plan does not include pictures: a hint replaces the field. */
  readonly pictureAllowed = input(true);
  readonly hintScope = input<'account' | 'adventure'>('account');
  readonly currentPlanId = input<string | null>(null);
  readonly saved = output<CharacterSubmission>();

  private readonly destroyRef = inject(DestroyRef);

  protected readonly backgroundMax = BACKGROUND_MAX;
  protected readonly pictureProblem = signal<string | null>(null);
  protected readonly previewUrl = signal<string | null>(null);
  private picture: File | null = null;

  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [trimmedLength(NAME_MIN, NAME_MAX)],
    }),
    race: new FormControl('', {
      nonNullable: true,
      validators: [trimmedLength(RACE_MIN, RACE_MAX)],
    }),
    martialClass: new FormControl('', {
      nonNullable: true,
      validators: [trimmedLength(1, CLASS_MAX)],
    }),
    personality: new FormControl('', {
      nonNullable: true,
      validators: [trimmedLength(1, PERSONALITY_MAX)],
    }),
    background: new FormControl('', {
      nonNullable: true,
      validators: [trimmedLength(1, BACKGROUND_MAX)],
    }),
  });

  private readonly background = toSignal(
    this.form.controls.background.valueChanges.pipe(startWith('')),
    { initialValue: '' },
  );
  protected readonly backgroundLength = computed(() => this.background().length);

  constructor() {
    // Every new `initial` replaces what is in the fields, edits included.
    effect(() => this.form.reset(this.initial()));
    this.destroyRef.onDestroy(() => this.revokePreview());
  }

  /** Puts the API's per-field messages on the fields; false when none matched. */
  showProblem(problem: ApiProblem): boolean {
    return applyFieldErrors(this.form, problem);
  }

  protected formatted(count: number): string {
    return count.toLocaleString(this.t().common.locale).replace(/\s/g, ' ');
  }

  protected async pick(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.picture = null;
    this.revokePreview();
    this.pictureProblem.set(null);
    if (file === null) {
      return;
    }
    const problem = await checkPicture(file, this.t().tavern.characters);
    if (problem !== null) {
      this.pictureProblem.set(problem);
      return;
    }
    this.picture = file;
    this.previewUrl.set(URL.createObjectURL(file));
  }

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.withPicture() && this.pictureAllowed() && this.pictureProblem() !== null) {
      // A refused file is shown as such; sending without it would silently drop the reader's choice.
      // Once the field has turned into the plan hint, no file is asked for: nothing to block.
      return;
    }
    const value = this.form.getRawValue();
    this.saved.emit({
      value: {
        name: value.name.trim(),
        race: value.race.trim(),
        martialClass: value.martialClass.trim(),
        personality: value.personality.trim(),
        background: value.background.trim(),
      },
      picture: this.withPicture() && this.pictureAllowed() ? this.picture : null,
    });
  }

  private revokePreview(): void {
    const url = this.previewUrl();
    if (url !== null) {
      URL.revokeObjectURL(url);
      this.previewUrl.set(null);
    }
  }
}
