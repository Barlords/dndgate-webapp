import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Emblem } from '../emblem/emblem';

/**
 * The full DnDGate logo: the name between two emblems. Its size follows the font size of the host
 * (the emblems scale with it). Decorative: the place that shows it names the site for assistive
 * technologies.
 */
@Component({
  selector: 'dg-brand',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Emblem],
  host: { 'aria-hidden': 'true' },
  template: `
    <dg-emblem />
    <span class="brand__name">DNDGATE</span>
    <dg-emblem />
  `,
  styles: `
    :host {
      --dg-emblem-size: 1.375em;
      display: inline-flex;
      align-items: center;
      gap: 0.625em;
      font-family: var(--dg-font-title);
      font-weight: 700;
      letter-spacing: 0.22em;
      color: var(--dg-gold-light);
    }

    // The letter spacing also follows the last letter: taken back so that the name stays centred.
    .brand__name {
      margin-right: -0.22em;
    }
  `,
})
export class Brand {}
