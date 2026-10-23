import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Button } from '../button/button';
import { FormField } from '../form-field/form-field';
import { Parchment } from '../parchment/parchment';

/** What the reader must type to confirm: a name to retype (`expected`) or a password. */
export interface ConfirmDialogInput {
  label: string;
  type: 'text' | 'password';
  expected?: string;
}

export interface ConfirmDialogData {
  titleId: string;
  messageId: string;
  title: string;
  message: string;
  confirmLabel: string;
  input?: ConfirmDialogInput;
}

/** `true` for a plain confirmation, the typed text when something had to be typed. */
export type ConfirmDialogResult = true | string;

/** Compared like the API does: accents in one encoding, spaces around ignored, case kept. */
function normalize(text: string): string {
  return text.normalize('NFC').trim();
}

@Component({
  selector: 'dg-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, FormField, Parchment, ReactiveFormsModule],
  template: `
    <dg-parchment>
      <form (submit)="submit($event)">
        <h2 class="dg-confirm__title" [id]="data.titleId">{{ data.title }}</h2>
        <p class="dg-confirm__message" [id]="data.messageId">{{ data.message }}</p>
        @if (data.input; as field) {
          <dg-form-field [label]="field.label" [control]="answer">
            <input
              [type]="field.type"
              [formControl]="answer"
              [attr.autocomplete]="field.type === 'password' ? 'current-password' : 'off'"
              spellcheck="false"
            />
          </dg-form-field>
        }
        <div class="dg-confirm__actions">
          <dg-button variant="ghost" (click)="cancel()">Annuler</dg-button>
          <dg-button
            type="submit"
            [variant]="data.input ? 'danger' : 'primary'"
            [disabled]="!canConfirm()"
          >
            {{ data.confirmLabel }}
          </dg-button>
        </div>
      </form>
    </dg-parchment>
  `,
  styles: `
    :host {
      display: block;
      animation: dg-unfurl 180ms ease-out;
      transform-origin: top center;
    }

    .dg-confirm__title {
      font-size: var(--dg-text-xl);
    }

    .dg-confirm__message {
      margin-bottom: var(--dg-space-5);
    }

    .dg-confirm__actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--dg-space-3);
      margin-top: var(--dg-space-5);
    }

    @keyframes dg-unfurl {
      from {
        opacity: 0;
        transform: scaleY(0.94);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      :host {
        animation: none;
      }
    }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<ConfirmDialogResult>>(DialogRef);

  protected readonly answer = new FormControl('', { nonNullable: true });
  private readonly typed = toSignal(this.answer.valueChanges, { initialValue: '' });

  protected readonly canConfirm = computed(() => {
    const field = this.data.input;
    if (!field) {
      return true;
    }
    const typed = this.typed();
    return field.expected === undefined
      ? typed.length > 0
      : normalize(typed) === normalize(field.expected);
  });

  protected submit(event: Event): void {
    event.preventDefault();
    if (this.canConfirm()) {
      // The typed text is returned as is: a password keeps its spaces.
      this.ref.close(this.data.input ? this.answer.value : true);
    }
  }

  protected cancel(): void {
    this.ref.close();
  }
}
