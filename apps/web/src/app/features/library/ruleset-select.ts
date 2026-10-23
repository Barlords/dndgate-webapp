import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { RulesetRegistry } from './rulesets/ruleset-registry';
import { SelectedRulesetService } from './rulesets/selected-ruleset.service';

/**
 * Native select of the registered rulesets, by full name. Choosing one opens it, on the same
 * section when the new ruleset has it.
 */
@Component({
  selector: 'dg-ruleset-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="ruleset-select">
      <span class="dg-visually-hidden">Jeu de règles</span>
      <select #field (change)="choose(field.value)">
        @for (ruleset of rulesets; track ruleset.id) {
          <option [value]="ruleset.id" [selected]="ruleset.id === current().id">
            {{ ruleset.label }}
          </option>
        }
      </select>
    </label>
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
  `,
})
export class RulesetSelect {
  private readonly router = inject(Router);
  private readonly registry = inject(RulesetRegistry);
  private readonly selected = inject(SelectedRulesetService);

  protected readonly rulesets = this.registry.all();
  protected readonly current = this.selected.current;

  protected choose(id: string): void {
    const section = this.selected.sectionId();
    const kept = this.registry.get(id)?.sections.some((candidate) => candidate.id === section);
    void this.router.navigate(kept ? ['/library', id, section] : ['/library', id]);
  }
}
