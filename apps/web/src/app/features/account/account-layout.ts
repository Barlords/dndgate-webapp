import { Location } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  ViewEncapsulation,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { I18n } from '../../core/i18n/i18n.service';
import { SiteFooter } from '../../layout/site-footer';
import { Emblem } from '../../shared/ui/emblem/emblem';
import { Icon } from '../../shared/ui/icon/icon';
import { Parchment } from '../../shared/ui/parchment/parchment';

/**
 * Page shell of the account screens: leather background, the emblem (back to the home page), the
 * parchment card and the site footer under it. These pages have no sidebar: a back button in the
 * top corner leaves them. `wide` widens the card for a page laid out in columns (the reader's own
 * account).
 */
@Component({
  selector: 'dg-account-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Not encapsulated: the pages project their content in and share these few rules.
  encapsulation: ViewEncapsulation.None,
  imports: [Emblem, Parchment, RouterLink, Icon, SiteFooter],
  template: `
    <main class="dg-account">
      <button type="button" class="dg-account__back" (click)="back()">
        <dg-icon name="chevron-left" />
        <span>{{ t().common.shell.back }}</span>
      </button>
      <a class="dg-account__home" routerLink="/" [attr.aria-label]="t().common.shell.home">
        <dg-emblem class="dg-account__emblem" />
      </a>
      <dg-parchment class="dg-account__card" [class.dg-account__card--wide]="wide()">
        <h1 class="dg-account__title">{{ heading() }}</h1>
        <ng-content />
      </dg-parchment>
      <dg-site-footer />
    </main>
  `,
  styles: `
    .dg-account {
      position: relative;
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

    .dg-account__home {
      display: block;
      border-radius: 50%;
      transition: filter var(--dg-transition);

      &:hover {
        filter: brightness(1.15);
      }
    }

    // In the top corner, quiet gold on the leather, as the controls of the sidebar.
    .dg-account__back {
      --dg-icon-size: 18px;
      position: absolute;
      top: var(--dg-space-4);
      left: var(--dg-space-4);
      display: inline-flex;
      align-items: center;
      gap: var(--dg-space-1);
      padding: var(--dg-space-1) var(--dg-space-3) var(--dg-space-1) var(--dg-space-2);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.08em;
      color: var(--dg-gold-light);
      background: none;
      border: 1px solid color-mix(in srgb, var(--dg-gold) 35%, transparent);
      border-radius: var(--dg-radius);
      cursor: pointer;
      transition:
        background var(--dg-transition),
        border-color var(--dg-transition);

      &:hover {
        background: color-mix(in srgb, var(--dg-gold) 12%, transparent);
        border-color: var(--dg-gold);
      }
    }

    // On a phone the button would sit on the emblem: it takes its own row.
    @media (max-width: 575.98px) {
      .dg-account__back {
        position: static;
        align-self: flex-start;
      }
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
  protected readonly t = inject(I18n).t;

  private readonly router = inject(Router);
  private readonly location = inject(Location);

  readonly heading = input.required<string>();
  readonly wide = input(false);

  /** Back to the page the reader came from; home when they opened this one directly. */
  protected back(): void {
    if (this.router.lastSuccessfulNavigation()?.previousNavigation) {
      this.location.back();
    } else {
      void this.router.navigateByUrl('/');
    }
  }
}
