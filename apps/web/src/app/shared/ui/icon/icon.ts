import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ICON_PATHS, IconName } from './icon-paths';

/**
 * Inline SVG icon drawn with the current text colour. Decorative unless a `label` is given.
 * `filled` paints the inside too (a chosen star, for instance).
 */
@Component({
  selector: 'dg-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      viewBox="0 0 24 24"
      [attr.fill]="filled() ? 'currentColor' : 'none'"
      stroke="currentColor"
      stroke-width="1.75"
      stroke-linecap="round"
      stroke-linejoin="round"
      [attr.role]="label() ? 'img' : null"
      [attr.aria-label]="label() || null"
      [attr.aria-hidden]="label() ? null : 'true'"
      focusable="false"
    >
      @for (path of paths(); track $index) {
        <path [attr.d]="path" />
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      width: var(--dg-icon-size, 1.25em);
      height: var(--dg-icon-size, 1.25em);
      flex: none;
      vertical-align: -0.2em;
    }

    svg {
      width: 100%;
      height: 100%;
    }
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly label = input<string>();
  readonly filled = input(false);

  protected readonly paths = computed(() => ICON_PATHS[this.name()]);
}
