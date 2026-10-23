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

let nextId = 0;

/** French message for one validation error; `server` carries the text the API gave. */
function describe(key: string, error: unknown): string {
  const details = (error ?? {}) as Record<string, unknown>;
  switch (key) {
    case 'required':
      return 'Ce champ est obligatoire.';
    case 'minlength':
      return `Saisis au moins ${details['requiredLength']} caractères.`;
    case 'maxlength':
      return `${details['requiredLength']} caractères au maximum.`;
    case 'min':
      return `Saisis un nombre supérieur ou égal à ${details['min']}.`;
    case 'max':
      return `Saisis un nombre inférieur ou égal à ${details['max']}.`;
    case 'dateRange':
      return 'La date doit être comprise entre 1900 et 2200.';
    case 'maxbytes':
      return `${details['max']} octets au maximum (les accents en comptent plusieurs).`;
    case 'mismatch':
      return 'Les deux mots de passe ne correspondent pas.';
    case 'pattern':
      return 'Le format attendu n’est pas respecté.';
    case 'email':
      return 'Cette adresse e-mail n’est pas valide.';
    case 'server':
      return typeof error === 'string' ? error : 'Cette valeur n’est pas valide.';
    default:
      return 'Cette valeur n’est pas valide.';
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
      .map(([key, error]) => describe(key, error));
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
