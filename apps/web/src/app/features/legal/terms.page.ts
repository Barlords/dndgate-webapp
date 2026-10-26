import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Dictionary } from '../../core/i18n/dictionary';
import { I18n } from '../../core/i18n/i18n.service';
import { AccountLayout } from '../account/account-layout';

type SectionId = keyof Dictionary['legal']['terms']['sections'];

/** The sections of the terms of sale, in reading order. */
const SECTIONS: readonly SectionId[] = [
  'publisher',
  'purpose',
  'offersAndPrices',
  'durationAndRenewal',
  'payment',
  'cancellation',
  'withdrawal',
  'messageHistoryDeletion',
  'personalData',
  'mediation',
  'governingLaw',
];

/**
 * The terms of sale, public. Only the structure is written: every section is a marked placeholder
 * until the legal text is validated, and a notice says so.
 */
@Component({
  selector: 'dg-terms-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AccountLayout, RouterLink],
  template: `
    <dg-account-layout [heading]="t().legal.terms.title" [wide]="true">
      <p class="terms__draft" role="note">{{ t().legal.terms.draftNotice }}</p>
      @for (id of sections; track id) {
        <section class="terms__section">
          <h2>{{ t().legal.terms.sections[id] }}</h2>
          <p class="terms__todo">{{ t().legal.terms.toComplete }}</p>
        </section>
      }
      <p class="terms__back">
        <a routerLink="/plans">{{ t().legal.terms.back }}</a>
      </p>
    </dg-account-layout>
  `,
  styles: `
    .terms__draft {
      margin: 0 0 var(--dg-space-4);
      padding: var(--dg-space-3);
      border: 1px dashed var(--dg-ink-muted);
      border-radius: 6px;
      font-weight: bold;
    }
    .terms__section h2 {
      margin: var(--dg-space-4) 0 var(--dg-space-2);
      font-size: var(--dg-text-lg);
    }
    .terms__todo {
      margin: 0;
      padding: var(--dg-space-2) var(--dg-space-3);
      border: 1px dashed var(--dg-ink-muted);
      border-radius: 6px;
      font-style: italic;
    }
    .terms__back {
      margin: var(--dg-space-5) 0 0;
      text-align: center;
    }
  `,
})
export class TermsPage {
  protected readonly t = inject(I18n).t;
  protected readonly sections = SECTIONS;
}
