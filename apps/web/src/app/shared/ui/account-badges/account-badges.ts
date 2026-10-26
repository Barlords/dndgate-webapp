import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import {
  AccountBadgeKind,
  AccountDirectory,
} from '../../../core/accounts/account-directory.service';
import { I18n } from '../../../core/i18n/i18n.service';

/**
 * Small marks right after a pseudo: « Fondateur » first, then the plan badge. Text, never colour
 * alone; nothing while the account loads, fails or has none. Inline, so it fits a table cell or a
 * chat line.
 */
@Component({
  selector: 'dg-account-badges',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      display: inline-flex;
      flex-wrap: wrap;
      gap: var(--dg-space-1);
      margin-left: var(--dg-space-1);
      vertical-align: middle;
    }
    .mark {
      padding: 0 var(--dg-space-2);
      border: 1px solid var(--dg-ink-muted);
      border-radius: 999px;
      font-family: var(--dg-font-body);
      font-size: 0.7rem;
      font-weight: 600;
      line-height: 1.4;
      white-space: nowrap;
      color: var(--dg-ink-muted);
    }
    .mark--founder {
      border-color: var(--dg-blood);
      color: var(--dg-blood);
    }
  `,
  template: `
    @for (mark of marks(); track mark.kind) {
      <span class="mark" [class.mark--founder]="mark.kind === 'founder'" [title]="mark.label">{{
        mark.label
      }}</span>
    }
  `,
})
export class AccountBadges {
  private readonly directory = inject(AccountDirectory);
  private readonly t = inject(I18n).t;

  readonly accountId = input<string | null | undefined>();

  protected readonly marks = computed(() => {
    const id = this.accountId();
    if (!id) {
      return [];
    }
    const names = this.t().plans.badges;
    const labels: Record<AccountBadgeKind, string> = {
      founder: names.founder,
      'dungeon-master': names.dungeonMaster,
      legend: names.legend,
    };
    return this.directory
      .badges(id)()
      .map((kind) => ({ kind, label: labels[kind] }));
  });
}
