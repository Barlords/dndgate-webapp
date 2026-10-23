import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Spinner } from '../spinner/spinner';

/** What a page loader says when its page has nothing more fitting. */
export const DEFAULT_LOADING_MESSAGE = 'Sélection des livres…';

/**
 * The loader of a whole page or panel: a large spinner over a line of flavour text ("Demande en
 * cours à Mystra…"). The flavour is decoration; `label` is the plain words announced to assistive
 * technologies. Buttons keep the small `dg-spinner`.
 */
@Component({
  selector: 'dg-loading',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Spinner],
  host: { role: 'status' },
  template: `
    <dg-spinner label="" />
    <p class="loading__message" aria-hidden="true">{{ message() }}</p>
    <span class="dg-visually-hidden">{{ label() }}</span>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dg-space-3);
      padding: var(--dg-space-4) 0;
      text-align: center;
    }

    dg-spinner {
      --dg-spinner-size: 4rem;
      // Light gold on leather, blood red on parchment: it shows on both.
      --dg-spinner-color: var(--dg-title-color);
    }

    .loading__message {
      margin: 0;
      font-style: italic;
      font-size: var(--dg-text-md);
      color: inherit;
      opacity: 0.85;
    }
  `,
})
export class Loading {
  readonly label = input('Chargement…');
  readonly message = input(DEFAULT_LOADING_MESSAGE);
}
