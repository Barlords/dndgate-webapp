import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '../core/i18n/i18n.service';

/** Under the content card of every page: the terms of sale and the copyright, quiet on the leather. */
@Component({
  selector: 'dg-site-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <footer class="footer" [attr.aria-label]="t().legal.footer.label">
      <a routerLink="/terms" [attr.title]="t().legal.footer.termsTitle">{{
        t().legal.footer.terms
      }}</a>
      <span class="footer__dot" aria-hidden="true">·</span>
      <small>{{ t().legal.footer.copyright }}</small>
    </footer>
  `,
  styles: `
    :host {
      display: block;
    }

    .footer {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
      gap: var(--dg-space-2);
      padding: var(--dg-space-4) 0 var(--dg-space-2);
      font-size: var(--dg-text-sm);
      color: color-mix(in srgb, var(--dg-text-light) 70%, transparent);
    }

    small {
      font-size: inherit;
    }

    a {
      color: var(--dg-gold-light);
      text-decoration: none;
      transition: color var(--dg-transition);

      &:hover {
        color: var(--dg-gold);
        text-decoration: underline;
      }
    }

    .footer__dot {
      color: var(--dg-gold);
    }
  `,
})
export class SiteFooter {
  protected readonly t = inject(I18n).t;
}
