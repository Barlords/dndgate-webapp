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
import { ApiProblem, toApiProblem } from '../../core/errors/api-problem';
import { I18n } from '../../core/i18n/i18n.service';
import { Billing } from '../../core/plans/billing.service';
import { PlanCatalog } from '../../core/plans/plan-catalog.service';
import { featureName, limitName, planName } from '../../core/plans/plan-texts';
import { Button } from '../../shared/ui/button/button';
import { LoadError } from '../../shared/ui/load-error/load-error';
import { Loading } from '../../shared/ui/loading/loading';

/**
 * The reader's offer: plan, billing period, renewal, usage of the account limits, what the plan
 * includes (every feature of the catalog, ticked or crossed out, and the other limits) and the ways to
 * change it (the offers page) or to manage it (the Stripe portal).
 */
@Component({
  selector: 'dg-subscription-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Loading, LoadError, RouterLink],
  template: `
    @if (failure(); as problem) {
      <dg-load-error
        [message]="t().plans.subscription.context"
        [problem]="problem"
        (retry)="load()"
      />
    } @else if (view(); as view) {
      <p class="plan">
        <span>{{ t().plans.subscription.plan }}</span>
        <strong class="plan__name">{{ view.name }}</strong>
        @if (view.badge; as badge) {
          <span class="seal seal--badge">{{ badge }}</span>
        }
      </p>
      @if (view.interval || view.status) {
        <p class="facts">
          @if (view.interval) {
            <span>{{ view.interval }}</span>
          }
          @if (view.status) {
            <span>{{ view.status }}</span>
          }
        </p>
      }
      @if (view.periodNote) {
        <p class="facts">{{ view.periodNote }}</p>
      }
      @if (view.pastDue) {
        <p class="warning" role="alert">{{ t().plans.subscription.pastDue }}</p>
      }

      @if (view.usage.length > 0) {
        <h3 class="usage__title">{{ t().plans.subscription.usage }}</h3>
        <ul class="usage">
          @for (item of view.usage; track item.key) {
            <li class="usage__item" [class.usage__item--full]="item.full">
              <span class="usage__label">
                <span>{{ item.label }}</span>
                <strong>{{ item.used }} / {{ item.max }}</strong>
              </span>
              <progress
                [value]="item.used"
                [max]="item.max"
                [attr.aria-label]="item.description"
              ></progress>
            </li>
          }
        </ul>
      }

      @if (view.limits.length > 0 || view.features.length > 0) {
        <h3 class="usage__title">{{ t().plans.subscription.included }}</h3>
        <ul class="included">
          @for (limit of view.limits; track limit.key) {
            <li class="included__limit">
              <span>{{ limit.label }}</span> <strong>{{ limit.value }}</strong>
            </li>
          }
          @for (feature of view.features; track feature.key) {
            <li
              class="included__feature"
              [class.included__feature--off]="!feature.included"
              [attr.aria-label]="feature.description"
            >
              <span class="included__mark" aria-hidden="true">{{
                feature.included ? '✓' : '✗'
              }}</span>
              <span aria-hidden="true">{{ feature.label }}</span>
            </li>
          }
        </ul>
      }

      <div class="actions">
        <a class="link link--primary" routerLink="/plans">{{ t().plans.subscription.change }}</a>
        @if (view.canManage) {
          <dg-button variant="ghost" [loading]="billing.busy()" (click)="billing.openPortal()">
            {{ t().plans.subscription.manage }}
          </dg-button>
        }
      </div>
      @if (!view.billingEnabled) {
        <p class="note">{{ t().plans.subscription.paymentUnavailable }}</p>
      }
    } @else {
      <dg-loading />
    }
  `,
  styles: `
    :host {
      display: block;
    }
    p {
      margin: 0 0 var(--dg-space-2);
    }
    .plan {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--dg-space-2);
    }
    .plan__name {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-lg);
      color: var(--dg-title-color);
    }
    .seal {
      padding: 0 var(--dg-space-2);
      border: 1px solid var(--dg-ink-muted);
      border-radius: 999px;
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }
    .facts {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-3);
      color: var(--dg-ink-muted);
    }
    .warning {
      padding: var(--dg-space-2) var(--dg-space-3);
      border: 1px solid var(--dg-blood);
      border-radius: var(--dg-radius-sm);
      color: var(--dg-blood);
      font-weight: 600;
    }
    .usage__title {
      margin: var(--dg-space-3) 0 var(--dg-space-1);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--dg-ink-muted);
    }
    .usage {
      display: flex;
      flex-direction: column;
      gap: var(--dg-space-2);
      margin: 0 0 var(--dg-space-3);
      padding: 0;
      list-style: none;
    }
    .usage__label {
      display: flex;
      justify-content: space-between;
      gap: var(--dg-space-2);
      font-size: var(--dg-text-sm);
    }
    progress {
      width: 100%;
      height: 0.5rem;
      accent-color: var(--dg-ink-muted);
    }
    .usage__item--full progress,
    .usage__item--full strong {
      accent-color: var(--dg-blood);
      color: var(--dg-blood);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--dg-space-3);
    }
    .link {
      padding: var(--dg-space-2) var(--dg-space-4);
      border: 1px solid var(--dg-blood);
      border-radius: 6px;
      color: var(--dg-blood);
      font-weight: 600;
      text-decoration: none;
    }
    .link--primary {
      color: var(--dg-parchment-0);
      background: var(--dg-blood);
      transition: background var(--dg-transition);

      &:hover {
        background: var(--dg-blood-bright);
      }
    }
    .link:focus-visible {
      outline: 2px solid var(--dg-focus-color);
      outline-offset: 2px;
    }
    .included {
      display: flex;
      flex-direction: column;
      margin: 0 0 var(--dg-space-4);
      padding: 0;
      list-style: none;
      font-size: var(--dg-text-sm);
    }
    .included li {
      display: flex;
      gap: var(--dg-space-2);
      padding: var(--dg-space-1) 0;
      border-bottom: 1px dotted var(--dg-ink-muted);
    }
    .included__limit {
      justify-content: space-between;
    }
    .included__mark {
      flex: 0 0 1rem;
      font-weight: 700;
      color: var(--dg-blood);
    }
    // What the plan leaves out is still listed, crossed and quieter.
    .included__feature--off {
      color: var(--dg-ink-muted);

      .included__mark {
        color: var(--dg-ink-muted);
      }

      span:last-child {
        text-decoration: line-through;
      }
    }
    .note {
      margin-top: var(--dg-space-2);
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }
  `,
})
export class SubscriptionPanel {
  protected readonly billing = inject(Billing);
  private readonly plans = inject(PlanCatalog);
  private readonly i18n = inject(I18n);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly t = this.i18n.t;

  protected readonly failure = signal<ApiProblem | null>(null);
  private readonly catalogLoaded = signal(false);

  protected readonly view = computed(() => {
    const subscription = this.billing.subscription();
    const catalog = this.plans.catalog();
    if (subscription === null || !this.catalogLoaded() || catalog === null) {
      return null;
    }
    const t = this.t();
    const language = this.i18n.language();
    const id = subscription.planId ?? '';
    const status = (subscription.status ?? '').toLowerCase();
    const interval = (subscription.interval ?? '').toLowerCase();
    const paid = id !== catalog.defaultPlan;
    const billingEnabled = subscription.billingEnabled !== false;

    const plan = this.plans.plan(id);
    const badge = plan?.badge;
    const usage = subscription.usage ?? {};
    const number = new Intl.NumberFormat(language);
    // Every feature some plan of the catalog has, in the catalog order: the reader sees what they miss too.
    const featureKeys = [
      ...new Set(
        (catalog.plans ?? []).flatMap((candidate) => Object.keys(candidate.features ?? {})),
      ),
    ];
    const intervals: Record<string, string> = t.plans.subscription.interval;
    const statuses: Record<string, string> = t.plans.subscription.status;
    const end = subscription.currentPeriodEnd;
    const date = end
      ? new Intl.DateTimeFormat(language, { dateStyle: 'long' }).format(new Date(end))
      : null;

    return {
      name: planName(id, t),
      badge:
        badge === 'dungeon-master'
          ? t.plans.badges.dungeonMaster
          : badge === 'legend'
            ? t.plans.badges.legend
            : null,
      interval: paid && Object.hasOwn(intervals, interval) ? intervals[interval] : null,
      status: paid && status ? (Object.hasOwn(statuses, status) ? statuses[status] : status) : null,
      periodNote:
        paid && date
          ? subscription.cancelAtPeriodEnd
            ? t.plans.subscription.ends(date)
            : t.plans.subscription.renews(date)
          : null,
      pastDue: status === 'past_due',
      canManage: paid && billingEnabled,
      billingEnabled,
      // The limits the usage bars do not show already (per adventure ones).
      limits: Object.entries(plan?.limits ?? {})
        .filter(([key]) => !Object.hasOwn(usage, key))
        .map(([key, value]) => ({ key, label: limitName(key, t), value: number.format(value) })),
      features: featureKeys.map((key) => {
        const label = featureName(key, t);
        const included = plan?.features?.[key] === true;
        return {
          key,
          label,
          included,
          description: included
            ? t.plans.subscription.featureIncluded(label)
            : t.plans.subscription.featureExcluded(label),
        };
      }),
      usage: Object.entries(usage).map(([key, { used = 0, max = 0 }]) => {
        const label = limitName(key, t);
        return {
          key,
          label,
          used,
          max,
          full: max > 0 && used >= max,
          description: t.plans.subscription.usageLabel(label, used, max),
        };
      }),
    };
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.failure.set(null);
    this.catalogLoaded.set(false);
    const fail = (error: unknown) => this.failure.set(toApiProblem(error));
    this.plans
      .load()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: () => this.catalogLoaded.set(true), error: fail });
    this.billing.loadMine().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ error: fail });
  }
}
