import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * The DnDGate emblem, read from `/images/emblem.svg` so that replacing that file changes it
 * everywhere. Decorative by default (it usually sits next to the name); give it an `alt` otherwise.
 */
@Component({
  selector: 'dg-emblem',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<img src="/images/emblem.svg" [alt]="alt()" width="100" height="100" />',
  styles: `
    :host {
      display: inline-flex;
      width: var(--dg-emblem-size, 2rem);
      height: var(--dg-emblem-size, 2rem);
      flex: none;
    }

    img {
      width: 100%;
      height: 100%;
      filter: drop-shadow(0 0 6px #d9b45a40);
    }
  `,
})
export class Emblem {
  readonly alt = input('');
}
