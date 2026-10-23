import { inject, Injectable } from '@angular/core';
import { Ruleset, RulesetId, RULESETS } from './ruleset.model';

/** The rulesets registered under `RULESETS` (multi provider), in registration order. */
@Injectable({ providedIn: 'root' })
export class RulesetRegistry {
  private readonly rulesets = inject(RULESETS, { optional: true }) ?? [];

  all(): Ruleset[] {
    return [...this.rulesets];
  }

  get(id: RulesetId): Ruleset | undefined {
    return this.rulesets.find((ruleset) => ruleset.id === id);
  }
}
