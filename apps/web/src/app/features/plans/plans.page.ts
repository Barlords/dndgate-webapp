import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiProblem, toApiProblem } from '../../core/errors/api-problem';
import { I18n } from '../../core/i18n/i18n.service';
import { Billing } from '../../core/plans/billing.service';
import { PlanCatalog } from '../../core/plans/plan-catalog.service';
import { Button } from '../../shared/ui/button/button';
import { LoadError } from '../../shared/ui/load-error/load-error';
import { Loading } from '../../shared/ui/loading/loading';
import { AccountLayout } from '../account/account-layout';
import { BillingInterval, PlanAction, PlanCard } from './plan-card';

/** The offers, public: what each plan gives and costs, and the way to subscribe or to manage. */
@Component({
  selector: 'dg-plans-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AccountLayout, PlanCard, Loading, LoadError, RouterLink, Button],
  template: `
    <dg-account-layout [heading]="t().plans.page.title" [wide]="true">
      <p class="plans__intro">{{ t().plans.page.intro }}</p>

      @if (failure(); as problem) {
        <dg-load-error [message]="t().plans.page.context" [problem]="problem" (retry)="load()" />
      } @else if (catalog(); as catalog) {
        <div class="plans__toggle" role="group" [attr.aria-label]="t().plans.page.billingPeriod">
          @for (option of intervals; track option) {
            <dg-button
              [variant]="interval() === option ? 'primary' : 'ghost'"
              [pressed]="interval() === option"
              (click)="interval.set(option)"
            >
              {{ option === 'monthly' ? t().plans.monthly : t().plans.yearly }}
            </dg-button>
          }
        </div>

        <ul class="plans__cards">
          @for (plan of catalog.plans; track plan.id) {
            <li>
              <dg-plan-card
                [plan]="plan"
                [currency]="catalog.currency ?? 'EUR'"
                [interval]="interval()"
                [action]="actionFor(plan.id ?? '')"
                [busy]="billing.busy() && clicked() === plan.id"
                (act)="act(plan.id ?? '')"
              />
            </li>
          }
        </ul>
        <p class="plans__terms">
          <a routerLink="/terms">{{ t().plans.page.terms }}</a>
        </p>
      } @else {
        <dg-loading />
      }
    </dg-account-layout>
  `,
  styles: `
    .plans__intro {
      margin: 0 0 var(--dg-space-4);
      text-align: center;
    }
    .plans__toggle {
      display: flex;
      justify-content: center;
      gap: var(--dg-space-2);
      margin-bottom: var(--dg-space-4);
    }
    .plans__cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(13rem, 100%), 1fr));
      gap: var(--dg-space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .plans__terms {
      margin: var(--dg-space-4) 0 0;
      text-align: center;
    }
  `,
})
export class PlansPage {
  protected readonly t = inject(I18n).t;
  protected readonly billing = inject(Billing);
  private readonly plans = inject(PlanCatalog);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly intervals: BillingInterval[] = ['monthly', 'yearly'];
  protected readonly interval = signal<BillingInterval>('monthly');
  protected readonly failure = signal<ApiProblem | null>(null);
  private readonly loaded = signal(false);
  /** This visit's answer about the reader's offer has come (the service may still hold an older one). */
  private readonly subscriptionLoaded = signal(false);
  protected readonly clicked = signal<string | null>(null);

  /** The catalog, shown once the reader's own offer is known too when there is a reader. */
  protected readonly catalog = computed(() =>
    this.loaded() && (!this.auth.isAuthenticated() || this.subscriptionLoaded())
      ? this.plans.catalog()
      : null,
  );

  constructor() {
    this.load();
  }

  protected load(): void {
    this.failure.set(null);
    this.loaded.set(false);
    this.subscriptionLoaded.set(false);
    this.plans
      .load()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.loaded.set(true),
        error: (error: unknown) => this.failure.set(toApiProblem(error)),
      });
    if (this.auth.isAuthenticated()) {
      this.billing
        .loadMine()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => this.subscriptionLoaded.set(true),
          error: (error: unknown) => this.failure.set(toApiProblem(error)),
        });
    }
  }

  protected actionFor(planId: string): PlanAction {
    const subscription = this.billing.subscription();
    if (!this.auth.isAuthenticated() || subscription === null) {
      return 'signIn';
    }
    if (subscription.planId === planId) {
      return 'current';
    }
    if (subscription.billingEnabled === false) {
      return 'unavailable';
    }
    // On the free offer one subscribes; on a paid one every change goes through the portal.
    return subscription.planId === this.plans.catalog()?.defaultPlan ? 'choose' : 'manage';
  }

  protected act(planId: string): void {
    this.clicked.set(planId);
    if (this.actionFor(planId) === 'manage') {
      this.billing.openPortal();
    } else {
      this.billing.checkout(planId, this.interval());
    }
  }
}
