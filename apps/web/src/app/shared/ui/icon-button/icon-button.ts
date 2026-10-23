import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Icon } from '../icon/icon';
import { IconName } from '../icon/icon-paths';
import { Spinner } from '../spinner/spinner';

export type IconButtonVariant = 'ghost' | 'danger';

/**
 * A round-cornered button showing only its icon; its `label` unfolds beside it on hover or
 * keyboard focus, and names it for assistive technologies unless `ariaLabel` says more (the
 * « Retirer » of a row is read « Retirer Shield du grimoire »). `danger` is drawn in outline
 * and fills with blood red on hover (removing, deleting). `badge` shows a count over the icon (the
 * filters in use). While `loading`, it is disabled and shows a spinner in place of the icon.
 */
@Component({
  selector: 'dg-icon-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Spinner],
  template: `
    <button
      [attr.type]="type()"
      [class]="'icon-button icon-button--' + variant()"
      [disabled]="disabled() || loading()"
      [attr.aria-label]="badge() ? name() + ', ' + badge() + ' actif(s)' : name()"
      [attr.aria-busy]="loading() || null"
      [attr.aria-expanded]="expanded()"
      [attr.aria-controls]="controls()"
    >
      @if (loading()) {
        <dg-spinner class="icon-button__spinner" label="" />
      } @else {
        <dg-icon [name]="icon()" />
      }
      @if (badge()) {
        <span class="icon-button__badge" aria-hidden="true">{{ badge() }}</span>
      }
      <span class="icon-button__label" aria-hidden="true">{{ label() }}</span>
    </button>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    // A disabled button must not let a click reach a handler bound on <dg-icon-button>.
    :host:has(button:disabled) {
      pointer-events: none;
    }

    .icon-button {
      position: relative;
      display: inline-flex;
      align-items: center;
      min-height: 2.25rem;
      padding: 0 0.55rem;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      text-transform: uppercase;
      white-space: nowrap;
      background: transparent;
      border: 1px solid color-mix(in srgb, currentColor 55%, transparent);
      border-radius: var(--dg-radius);
      cursor: pointer;
      transition:
        color var(--dg-transition),
        background-color var(--dg-transition),
        border-color var(--dg-transition);

      &:disabled {
        cursor: not-allowed;
        opacity: 0.55;
      }
    }

    .icon-button--ghost {
      color: var(--dg-quiet-color);

      &:hover:not(:disabled),
      &[aria-expanded='true'] {
        background: color-mix(in srgb, currentColor 10%, transparent);
        border-color: currentColor;
      }
    }

    // Outlined at rest, filled once hovered: the action is about to be taken.
    .icon-button--danger {
      color: var(--dg-blood);

      &:hover:not(:disabled),
      &:focus-visible {
        color: var(--dg-parchment-0);
        background: var(--dg-blood);
        border-color: var(--dg-blood);
      }
    }

    .icon-button__spinner {
      --dg-spinner-size: 1.25em;
      --dg-spinner-color: currentColor;
    }

    .icon-button__badge {
      position: absolute;
      top: -0.45rem;
      right: -0.45rem;
      min-width: 1.15rem;
      height: 1.15rem;
      padding: 0 0.25rem;
      font-family: var(--dg-font-body);
      font-size: 0.7rem;
      font-weight: 600;
      line-height: 1.15rem;
      letter-spacing: 0;
      text-align: center;
      color: var(--dg-parchment-0);
      background: var(--dg-blood);
      border-radius: 999px;
    }

    // Folded to nothing until the button is hovered, focused or open.
    .icon-button__label {
      display: inline-block;
      max-width: 0;
      overflow: hidden;
      opacity: 0;
      transition:
        max-width 250ms ease-out,
        opacity 250ms ease-out,
        margin-left 250ms ease-out;

      @media (prefers-reduced-motion: reduce) {
        transition: none;
      }
    }

    .icon-button:hover:not(:disabled),
    .icon-button:focus-visible,
    .icon-button[aria-expanded='true'] {
      .icon-button__label {
        max-width: 14rem;
        margin-left: var(--dg-space-2);
        opacity: 1;
      }
    }
  `,
})
export class IconButton {
  readonly icon = input.required<IconName>();
  readonly label = input.required<string>();
  readonly ariaLabel = input<string | null>(null);
  readonly variant = input<IconButtonVariant>('ghost');
  readonly type = input<'button' | 'submit'>('button');
  readonly disabled = input(false);
  readonly loading = input(false);
  readonly badge = input<number | null>(null);
  /** For a button that opens a panel: whether it is open, and the panel's id. */
  readonly expanded = input<boolean | null>(null);
  readonly controls = input<string | null>(null);

  protected readonly name = computed(() => this.ariaLabel() ?? this.label());
}
