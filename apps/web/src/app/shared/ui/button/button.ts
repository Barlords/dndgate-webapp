import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Spinner } from '../spinner/spinner';

export type ButtonVariant = 'primary' | 'ghost' | 'danger';

/**
 * Action button. `primary` is the blood-red seal edged with gold, `ghost` a quiet outlined action,
 * `danger` the dark red of irreversible actions. While `loading`, it is disabled and shows a spinner.
 */
@Component({
  selector: 'dg-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Spinner],
  template: `
    <button
      [attr.type]="type()"
      [class]="'dg-button dg-button--' + variant()"
      [disabled]="disabled() || loading()"
      [attr.aria-busy]="loading() || null"
      [attr.aria-pressed]="pressed()"
    >
      @if (loading()) {
        <dg-spinner class="dg-button__spinner" label="" />
      }
      <span class="dg-button__label"><ng-content /></span>
    </button>
  `,
  styleUrl: './button.scss',
})
export class Button {
  readonly variant = input<ButtonVariant>('primary');
  readonly type = input<'button' | 'submit'>('button');
  readonly disabled = input(false);
  readonly loading = input(false);
  /** For a toggle button: whether it is on. Left `null`, the button is not a toggle. */
  readonly pressed = input<boolean | null>(null);
}
