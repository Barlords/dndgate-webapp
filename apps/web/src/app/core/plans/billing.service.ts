import { HttpClient } from '@angular/common/http';
import { inject, Injectable, InjectionToken, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { defer, Observable, switchMap, tap } from 'rxjs';
import { ApiConfiguration } from '../api/generated/api-configuration';
import {
  createCheckoutSession,
  createPortalSession,
  getMySubscription,
} from '../api/generated/functions';
import { RedirectDto } from '../api/generated/models/redirect-dto';
import { SubscriptionDto } from '../api/generated/models/subscription-dto';
import { AuthService } from '../auth/auth.service';
import { toApiProblem } from '../errors/api-problem';
import { NotifierService } from '../errors/notifier.service';
import { I18n } from '../i18n/i18n.service';

/** Leaves the site for an address (Stripe); a token so that tests can observe it. */
export const REDIRECT = new InjectionToken<(url: string) => void>('REDIRECT', {
  providedIn: 'root',
  factory: () => (url) => window.location.assign(url),
});

/** The reader's subscription and the two ways out to Stripe: Checkout to subscribe, the portal to manage. */
@Injectable({ providedIn: 'root' })
export class Billing {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly notifier = inject(NotifierService);
  private readonly redirect = inject(REDIRECT);
  private readonly t = inject(I18n).t;

  constructor() {
    // Another reader may sign in next: nothing of this one's offer may be shown to them.
    inject(AuthService)
      .sessionEnded.pipe(takeUntilDestroyed())
      .subscribe(() => this.current.set(null));
  }

  private readonly current = signal<SubscriptionDto | null>(null);
  /** What `GET /accounts/me/subscription` last answered: never the browser return of Checkout. */
  readonly subscription = this.current.asReadonly();

  private readonly running = signal(false);
  /** A call to Stripe is in flight: the buttons wait. */
  readonly busy = this.running.asReadonly();

  loadMine(): Observable<SubscriptionDto> {
    return defer(() => {
      // A reload never shows the previous answer while the new one is pending.
      this.current.set(null);
      return getMySubscription(this.http, this.rootUrl);
    }).pipe(
      switchMap((response) => [response.body]),
      tap((subscription) => this.current.set(subscription)),
    );
  }

  checkout(planId: string, interval: 'monthly' | 'yearly'): void {
    this.leave(
      createCheckoutSession(this.http, this.rootUrl, { body: { planId, interval } }),
      this.t().plans.page.checkoutContext,
      // Already subscribed: changing plan happens in the portal.
      (status) => {
        if (status === 409) {
          this.openPortal();
          return true;
        }
        return false;
      },
    );
  }

  openPortal(): void {
    this.leave(createPortalSession(this.http, this.rootUrl), this.t().plans.page.portalContext);
  }

  private leave(
    call: Observable<{ body: RedirectDto }>,
    context: string,
    handled: (status: number) => boolean = () => false,
  ): void {
    if (this.running()) {
      return;
    }
    this.running.set(true);
    call.subscribe({
      next: (response) => {
        if (response.body.url) {
          // Stays busy: the page is about to be left, a second click must not open a second session.
          this.redirect(response.body.url);
        } else {
          this.running.set(false);
          this.notifier.failure(this.t().plans.redirectMissing);
        }
      },
      error: (error: unknown) => {
        this.running.set(false);
        const problem = toApiProblem(error);
        if (handled(problem.status)) {
          return;
        }
        if (problem.status === 503) {
          this.notifier.failure(this.t().plans.billingUnavailable);
        } else {
          this.notifier.error(problem, context);
        }
      },
    });
  }
}
