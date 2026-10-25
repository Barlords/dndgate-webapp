import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  catchError,
  EMPTY,
  exhaustMap,
  Subscription,
  switchMap,
  take,
  takeWhile,
  tap,
  timer,
} from 'rxjs';
import { SubscriptionDto } from '../../core/api/generated/models/subscription-dto';
import { ApiProblem, toApiProblem } from '../../core/errors/api-problem';
import { I18n } from '../../core/i18n/i18n.service';
import { Billing } from '../../core/plans/billing.service';
import { PlanCatalog } from '../../core/plans/plan-catalog.service';
import { planName } from '../../core/plans/plan-texts';
import { Button } from '../../shared/ui/button/button';
import { LoadError } from '../../shared/ui/load-error/load-error';
import { Loading } from '../../shared/ui/loading/loading';
import { AccountLayout } from '../account/account-layout';

/** `SubscriptionDto.status` values under which the plan applies. */
const GRANTING = ['active', 'trialing', 'past_due'];
const PERIOD_MS = 2000;
const MAX_TRIES = 15;

/**
 * Where Stripe Checkout returns. The browser return proves nothing: the plan is the one the API
 * answers, and it changes when the webhook arrives, so the page asks again until it does.
 */
@Component({
  selector: 'dg-checkout-success-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AccountLayout, Loading, LoadError, RouterLink, Button],
  template: `
    <dg-account-layout [heading]="t().plans.success.title">
      @if (failure(); as problem) {
        <dg-load-error
          [message]="t().plans.success.context"
          [problem]="problem"
          (retry)="start()"
        />
      } @else if (grantedPlan(); as planId) {
        <p role="status">{{ t().plans.success.welcome(name(planId)) }}</p>
        <p class="success__links">
          <a routerLink="/account">{{ t().plans.success.toProfile }}</a>
          <a routerLink="/tavern">{{ t().plans.success.toTavern }}</a>
        </p>
      } @else if (waiting()) {
        <p role="status">{{ t().plans.success.checking }}</p>
        <dg-loading />
      } @else {
        <p role="status">{{ t().plans.success.pending }}</p>
        <dg-button variant="ghost" (click)="start()">{{ t().plans.success.checkAgain }}</dg-button>
      }
    </dg-account-layout>
  `,
  styles: `
    .success__links {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-4);
    }
  `,
})
export class CheckoutSuccessPage {
  protected readonly t = inject(I18n).t;
  private readonly billing = inject(Billing);
  private readonly plans = inject(PlanCatalog);
  private readonly destroyRef = inject(DestroyRef);

  /** The plan the API answered, once it applies and is not the free one. */
  protected readonly grantedPlan = signal<string | null>(null);
  protected readonly waiting = signal(true);
  protected readonly failure = signal<ApiProblem | null>(null);
  private polling?: Subscription;

  constructor() {
    this.destroyRef.onDestroy(() => this.polling?.unsubscribe());
    this.start();
  }

  protected name(planId: string): string {
    return planName(planId, this.t());
  }

  /** A fresh cycle of tries; also what "check again" does. */
  protected start(): void {
    this.polling?.unsubscribe();
    this.failure.set(null);
    this.grantedPlan.set(null);
    this.waiting.set(true);
    this.polling = this.plans
      .load()
      .pipe(
        switchMap((catalog) =>
          timer(0, PERIOD_MS).pipe(
            take(MAX_TRIES),
            // One call at a time; a try that fails is a try used, not the end of the wait.
            exhaustMap(() => this.billing.loadMine().pipe(catchError(() => EMPTY))),
            tap((subscription) => this.grant(subscription, catalog.defaultPlan)),
            takeWhile(() => this.grantedPlan() === null),
          ),
        ),
      )
      .subscribe({
        complete: () => this.waiting.set(false),
        error: (error: unknown) => {
          this.waiting.set(false);
          this.failure.set(toApiProblem(error));
        },
      });
  }

  private grant(subscription: SubscriptionDto, defaultPlan: string | undefined): void {
    if (
      GRANTING.includes(subscription.status ?? '') &&
      subscription.planId &&
      subscription.planId !== defaultPlan
    ) {
      this.grantedPlan.set(subscription.planId);
    }
  }
}
