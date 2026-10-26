import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PlanDto } from '../../core/api/generated/models/plan-dto';
import { I18n } from '../../core/i18n/i18n.service';
import { featureName, formatPrice, limitName, planName } from '../../core/plans/plan-texts';
import { Button } from '../../shared/ui/button/button';

export type BillingInterval = 'monthly' | 'yearly';

/** What the card's button does for this reader. */
export type PlanAction = 'signIn' | 'current' | 'choose' | 'manage' | 'unavailable';

/** One offer of the catalog: its name, price, limits and features, and the one action left to the reader. */
@Component({
  selector: 'dg-plan-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, RouterLink],
  template: `
    <article class="card" [class.card--highlighted]="plan().highlighted">
      <header>
        <div class="card__seals">
          @if (plan().highlighted) {
            <span class="seal seal--chosen">{{ t().plans.mostChosen }}</span>
          }
          @if (badge(); as label) {
            <span class="seal seal--badge">{{ label }}</span>
          }
        </div>
        <h2 class="card__name">{{ name() }}</h2>
        <p class="card__tagline">{{ tagline() }}</p>
      </header>

      <p class="card__price">
        @if (periodMissing()) {
          <span class="card__unavailable">{{ t().plans.unavailableFor[interval()] }}</span>
        } @else if (amount() === null) {
          <span class="card__amount">{{ t().plans.free }}</span>
        } @else {
          <span class="card__amount">{{ amount() }}</span>
          <span class="card__period">{{ period() }}</span>
        }
      </p>
      <p class="card__note">
        @if (amount() !== null && interval() === 'yearly') {
          {{ t().plans.twoMonthsFree }}
        }
      </p>

      <ul class="card__list" [attr.aria-label]="t().plans.page.limitsHeading">
        @for (limit of limits(); track limit.key) {
          <li>
            <span>{{ limit.label }}</span> <strong>{{ limit.value }}</strong>
          </li>
        }
      </ul>
      @if (features().length > 0) {
        <ul
          class="card__list card__list--features"
          [attr.aria-label]="t().plans.page.includedFeatures"
        >
          @for (feature of features(); track feature) {
            <li>{{ feature }}</li>
          }
        </ul>
      }

      <div class="card__action">
        @switch (shownAction()) {
          @case ('signIn') {
            <a class="card__link" routerLink="/login" [queryParams]="{ returnUrl: '/plans' }">
              {{ t().plans.page.signIn }}
            </a>
          }
          @case ('current') {
            <dg-button variant="ghost" [disabled]="true">{{ t().plans.page.current }}</dg-button>
          }
          @case ('choose') {
            <dg-button [loading]="busy()" (click)="act.emit()">
              {{ t().plans.page.choose }}
            </dg-button>
          }
          @case ('manage') {
            <dg-button variant="ghost" [loading]="busy()" (click)="act.emit()">
              {{ t().plans.page.manage }}
            </dg-button>
          }
          @case ('unavailable') {
            <dg-button variant="ghost" [disabled]="true">
              {{ t().plans.page.unavailable }}
            </dg-button>
          }
        }
      </div>
    </article>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }
    .card {
      display: flex;
      flex-direction: column;
      gap: var(--dg-space-2);
      height: 100%;
      box-sizing: border-box;
      padding: var(--dg-space-4);
      border: 1px solid var(--dg-ink-muted);
      border-radius: 6px;
      background: rgb(255 255 255 / 0.25);
    }
    .card--highlighted {
      border: 2px solid var(--dg-blood);
    }
    .card__seals {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-1);
      min-height: 1.5rem;
    }
    .seal {
      padding: 0 var(--dg-space-2);
      border: 1px solid var(--dg-ink-muted);
      border-radius: 999px;
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }
    .seal--chosen {
      color: var(--dg-parchment-0);
      background: var(--dg-blood);
      border-color: var(--dg-blood);
    }
    .card__name {
      margin: 0;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-lg);
      color: var(--dg-title-color);
    }
    .card__tagline,
    .card__note {
      margin: 0;
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }
    .card__note {
      min-height: 1.25rem;
      color: var(--dg-blood);
      font-weight: 600;
    }
    .card__price {
      margin: var(--dg-space-2) 0 0;
    }
    .card__amount {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-xl);
    }
    .card__period {
      margin-left: var(--dg-space-1);
      color: var(--dg-ink-muted);
    }
    .card__list {
      margin: 0;
      padding: 0;
      list-style: none;
      font-size: var(--dg-text-sm);
    }
    .card__list li {
      display: flex;
      justify-content: space-between;
      gap: var(--dg-space-2);
      padding: var(--dg-space-1) 0;
      border-bottom: 1px dotted var(--dg-ink-muted);
    }
    .card__list--features li {
      justify-content: flex-start;
    }
    .card__list--features li::before {
      content: '\\2713';
      color: var(--dg-blood);
    }
    .card__action {
      display: flex;
      justify-content: center;
      margin-top: auto;
      padding-top: var(--dg-space-3);
    }
    .card__unavailable {
      font-style: italic;
      color: var(--dg-ink-muted);
    }
    .card__link {
      padding: var(--dg-space-2) var(--dg-space-4);
      border: 1px solid var(--dg-blood);
      border-radius: 6px;
      color: var(--dg-blood);
      font-weight: 600;
      text-decoration: none;
    }
    .card__link:focus-visible {
      outline: 2px solid var(--dg-focus-color);
      outline-offset: 2px;
    }
  `,
})
export class PlanCard {
  private readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;

  readonly plan = input.required<PlanDto>();
  readonly currency = input.required<string>();
  readonly interval = input.required<BillingInterval>();
  readonly action = input.required<PlanAction>();
  readonly busy = input(false);
  /** The button was pressed (`choose` or `manage`). */
  readonly act = output<void>();

  private readonly id = computed(() => this.plan().id ?? '');
  protected readonly name = computed(() => planName(this.id(), this.t()));
  protected readonly tagline = computed(() => {
    const taglines: Record<string, string> = this.t().plans.taglines;
    return Object.hasOwn(taglines, this.id()) ? taglines[this.id()] : '';
  });

  protected readonly badge = computed(() => {
    const badge = this.plan().badge;
    if (badge === 'dungeon-master') {
      return this.t().plans.badges.dungeonMaster;
    }
    return badge === 'legend' ? this.t().plans.badges.legend : null;
  });

  /** The price of the chosen period; null for the free offer (it has none). */
  protected readonly amount = computed(() => {
    const price = this.plan().prices?.[this.interval()];
    return price?.amount === undefined
      ? null
      : formatPrice(price.amount, this.currency(), this.i18n.language());
  });
  /** A paid plan sold in the other period only: nothing to show or choose for this one. */
  protected readonly periodMissing = computed(() => {
    const prices = this.plan().prices ?? {};
    return Object.keys(prices).length > 0 && prices[this.interval()]?.amount === undefined;
  });
  /** Without a price for the period there is nothing to buy; « Ton offre » still says where one is. */
  protected readonly shownAction = computed(() =>
    this.periodMissing() && this.action() !== 'current' ? null : this.action(),
  );
  protected readonly period = computed(() =>
    this.interval() === 'yearly' ? this.t().plans.perYear : this.t().plans.perMonth,
  );

  protected readonly limits = computed(() =>
    Object.entries(this.plan().limits ?? {}).map(([key, value]) => ({
      key,
      label: limitName(key, this.t()),
      value: new Intl.NumberFormat(this.i18n.language()).format(value),
    })),
  );
  protected readonly features = computed(() =>
    Object.entries(this.plan().features ?? {})
      .filter(([, included]) => included)
      .map(([key]) => featureName(key, this.t())),
  );
}
