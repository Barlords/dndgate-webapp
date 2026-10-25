import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '../../../core/i18n/i18n.service';
import { PlanCatalog } from '../../../core/plans/plan-catalog.service';
import { planName } from '../../../core/plans/plan-texts';

/**
 * A short line saying which plan unlocks a feature, with a link to the plans. It names the lowest
 * plan, after the reader's own when given, whose features include it.
 * `adventure` adds that a GM with an equipped table also unlocks it for the whole table.
 */
@Component({
  selector: 'dg-plan-hint',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  styles: `
    :host {
      display: block;
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }
  `,
  template: `
    {{ text() }}
    <a routerLink="/plans">{{ t().plans.seeOffers }}</a>
  `,
})
export class PlanHint {
  protected readonly t = inject(I18n).t;
  private readonly catalog = inject(PlanCatalog);

  /** Key of the feature, as in a plan's `features`. */
  readonly feature = input.required<string>();
  /** Where the reader is: in their account, or in an adventure (the GM's table counts too). */
  readonly scope = input<'account' | 'adventure'>('account');
  /** The reader's own plan, when known: only the plans after it are proposed. */
  readonly currentPlanId = input<string | null>(null);

  constructor() {
    // The hint is useful without the names, so a failure to load them is not reported here.
    this.catalog.load().subscribe({ error: () => undefined });
  }

  private readonly unlocking = computed(() => {
    const plans = this.catalog.catalog()?.plans ?? [];
    const current = plans.findIndex((plan) => plan.id === this.currentPlanId());
    return plans.slice(current + 1).find((plan) => plan.features?.[this.feature()] === true)?.id;
  });

  protected readonly text = computed(() => {
    const hints = this.t().plans.hint;
    const id = this.unlocking();
    if (id === undefined) {
      return hints.unknown;
    }
    const name = planName(id, this.t());
    return this.scope() === 'adventure' ? hints.adventure(name) : hints.account(name);
  });
}
