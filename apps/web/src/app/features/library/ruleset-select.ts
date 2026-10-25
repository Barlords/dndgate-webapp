import { CdkMenu, CdkMenuItemRadio, CdkMenuTrigger } from '@angular/cdk/menu';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { SidebarStateService } from '../../layout/sidebar-state.service';
import { Icon } from '../../shared/ui/icon/icon';
import { RulesetRegistry } from './rulesets/ruleset-registry';
import { SelectedRulesetService } from './rulesets/selected-ruleset.service';
import { I18n } from '../../core/i18n/i18n.service';

/**
 * The registered rulesets, by full name: a native select in the open sidebar, and in the folded one
 * a list icon of the same height opening a menu of them. Choosing one opens it, on the same section
 * when the new ruleset has it.
 */
@Component({
  selector: 'dg-ruleset-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkMenuTrigger, CdkMenu, CdkMenuItemRadio, Icon],
  template: `
    @if (collapsed()) {
      <button
        type="button"
        class="ruleset-select__icon"
        [cdkMenuTriggerFor]="menu"
        aria-haspopup="menu"
        [attr.aria-label]="t().library.rulesetSelect.current(current().label)"
        [attr.title]="t().library.rulesetSelect.current(current().label)"
      >
        <dg-icon name="list" />
      </button>
      <ng-template #menu>
        <ul
          cdkMenu
          class="ruleset-select__menu"
          [attr.aria-label]="t().library.rulesetSelect.label"
        >
          @for (ruleset of rulesets; track ruleset.id) {
            <li
              cdkMenuItemRadio
              class="ruleset-select__item"
              [cdkMenuItemChecked]="ruleset.id === current().id"
              (cdkMenuItemTriggered)="choose(ruleset.id)"
            >
              <span class="ruleset-select__mark" aria-hidden="true">{{
                ruleset.id === current().id ? '✓' : ''
              }}</span>
              {{ ruleset.label }}
            </li>
          }
        </ul>
      </ng-template>
    } @else {
      <label class="ruleset-select">
        <span class="dg-visually-hidden">{{ t().library.rulesetSelect.label }}</span>
        <select #field (change)="choose(field.value)">
          @for (ruleset of rulesets; track ruleset.id) {
            <option [value]="ruleset.id" [selected]="ruleset.id === current().id">
              {{ ruleset.label }}
            </option>
          }
        </select>
      </label>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .ruleset-select {
      position: relative;
      display: block;

      // Gold chevron in place of the native arrow.
      &::after {
        content: '';
        position: absolute;
        top: 50%;
        right: 0.8rem;
        width: 0.45rem;
        height: 0.45rem;
        border-right: 1.5px solid var(--dg-gold);
        border-bottom: 1.5px solid var(--dg-gold);
        transform: translateY(-70%) rotate(45deg);
        pointer-events: none;
      }
    }

    select {
      width: 100%;
      min-height: 2.5rem;
      padding: 0.4rem 2rem 0.4rem 0.7rem;
      font-family: var(--dg-font-body);
      font-size: var(--dg-text-sm);
      color: var(--dg-text-light);
      background: linear-gradient(var(--dg-leather-1), var(--dg-leather-0));
      border: 1px solid color-mix(in srgb, var(--dg-gold) 55%, transparent);
      border-radius: var(--dg-radius-sm);
      appearance: none;
      cursor: pointer;
      transition: border-color var(--dg-transition);

      &:hover,
      &:focus {
        border-color: var(--dg-gold);
      }
    }

    option {
      color: var(--dg-ink);
      background: var(--dg-parchment-0);
    }

    // As tall as the select, its icon where the other icons of the rail are.
    .ruleset-select__icon {
      --dg-icon-size: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 2.5rem;
      padding: 0;
      color: var(--dg-gold-light);
      background: linear-gradient(var(--dg-leather-1), var(--dg-leather-0));
      border: 1px solid color-mix(in srgb, var(--dg-gold) 55%, transparent);
      border-radius: var(--dg-radius-sm);
      cursor: pointer;
      transition: border-color var(--dg-transition);

      &:hover,
      &[aria-expanded='true'] {
        border-color: var(--dg-gold);
      }
    }

    .ruleset-select__menu {
      min-width: 14rem;
      margin: 0 0 0 var(--dg-space-2);
      padding: var(--dg-space-1);
      list-style: none;
      font-family: var(--dg-font-body);
      font-size: var(--dg-text-sm);
      color: var(--dg-text-light);
      background: var(--dg-leather-0);
      border: 1px solid color-mix(in srgb, var(--dg-gold) 55%, transparent);
      border-radius: var(--dg-radius);
      box-shadow: 0 6px 18px #000000aa;
    }

    .ruleset-select__item {
      display: flex;
      align-items: center;
      gap: var(--dg-space-2);
      padding: var(--dg-space-2) var(--dg-space-3);
      border-radius: var(--dg-radius-sm);
      cursor: pointer;

      &:hover,
      &.cdk-menu-item-active,
      &:focus-visible {
        background: #d9b45a26;
      }
    }

    .ruleset-select__mark {
      flex: 0 0 1rem;
      color: var(--dg-gold);
    }
  `,
})
export class RulesetSelect {
  protected readonly t = inject(I18n).t;

  private readonly router = inject(Router);
  private readonly registry = inject(RulesetRegistry);
  private readonly selected = inject(SelectedRulesetService);

  protected readonly rulesets = this.registry.all();
  protected readonly current = this.selected.current;
  protected readonly collapsed = inject(SidebarStateService).collapsed;

  protected choose(id: string): void {
    const section = this.selected.sectionId();
    const kept = this.registry.get(id)?.sections.some((candidate) => candidate.id === section);
    void this.router.navigate(kept ? ['/library', id, section] : ['/library', id]);
  }
}
