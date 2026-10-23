import { ChangeDetectionStrategy, Component, input, ViewEncapsulation } from '@angular/core';
import { Emblem } from '../../shared/ui/emblem/emblem';
import { Parchment } from '../../shared/ui/parchment/parchment';

/**
 * Page shell of the account screens: leather background, emblem, parchment card. `wide` widens the
 * card for a page laid out in columns (the reader's own account).
 */
@Component({
  selector: 'dg-account-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Not encapsulated: the pages project their content in and share these few rules.
  encapsulation: ViewEncapsulation.None,
  imports: [Emblem, Parchment],
  template: `
    <main class="dg-account">
      <dg-emblem class="dg-account__emblem" />
      <dg-parchment class="dg-account__card" [class.dg-account__card--wide]="wide()">
        <h1 class="dg-account__title">{{ heading() }}</h1>
        <ng-content />
      </dg-parchment>
    </main>
  `,
  styles: `
    .dg-account {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: var(--dg-space-5);
      min-height: 100vh;
      padding: var(--dg-space-5) var(--dg-space-4);
      background: radial-gradient(circle at top, var(--dg-leather-1), var(--dg-leather-0) 70%);
    }

    .dg-account__emblem {
      --dg-emblem-size: 4.5rem;
    }

    .dg-account__card {
      width: min(28rem, 100%);
    }

    .dg-account__title {
      margin: 0 0 var(--dg-space-5);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-xl);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      text-align: center;
      color: var(--dg-title-color);
    }

    .dg-account__card--wide {
      width: min(64rem, 100%);
    }

    .dg-account__card form {
      display: flex;
      flex-direction: column;
      gap: var(--dg-space-3);
    }

    .dg-account__card p {
      margin: 0 0 var(--dg-space-4);
    }

    .dg-account__card a {
      color: var(--dg-blood);
    }

    .dg-account__alert {
      color: var(--dg-blood);
      font-weight: 600;
    }

    .dg-account__actions {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dg-space-3);
      margin-top: var(--dg-space-4);
      text-align: center;
    }
  `,
})
export class AccountLayout {
  readonly heading = input.required<string>();
  readonly wide = input(false);
}
