import { computed, inject, Injectable, signal } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { SafeStorage } from '../../../core/storage/safe-storage';
import { RulesetRegistry } from './ruleset-registry';
import { Ruleset, RulesetId } from './ruleset.model';

const STORAGE_KEY = 'dg.ruleset';
const DEFAULT_RULESET: RulesetId = 'dnd5e-2014';

interface LibraryParams {
  rulesetId: string | null;
  sectionId: string | null;
}

/** The `rulesetId` and `sectionId` parameters of the current URL, wherever they sit in the tree. */
function readParams(root: ActivatedRouteSnapshot): LibraryParams {
  const params: LibraryParams = { rulesetId: null, sectionId: null };
  for (let route: ActivatedRouteSnapshot | null = root; route !== null; route = route.firstChild) {
    params.rulesetId = route.paramMap.get('rulesetId') ?? params.rulesetId;
    params.sectionId = route.paramMap.get('sectionId') ?? params.sectionId;
  }
  return params;
}

/**
 * The ruleset the Library shows: the one of the URL (`:rulesetId`), else the last one the reader
 * opened (remembered in the browser), else `dnd5e-2014`, else the first registered.
 */
@Injectable({ providedIn: 'root' })
export class SelectedRulesetService {
  private readonly registry = inject(RulesetRegistry);
  private readonly storage = inject(SafeStorage);
  private readonly router = inject(Router);

  private readonly params = signal(readParams(this.router.routerState.snapshot.root));
  private readonly remembered = signal(this.storage.get(STORAGE_KEY));

  /** The section of the URL, if any (another ruleset may not have it). */
  readonly sectionId = computed(() => this.params().sectionId);

  readonly current = computed<Ruleset>(() => {
    const ruleset =
      this.known(this.params().rulesetId) ??
      this.known(this.remembered()) ??
      this.known(DEFAULT_RULESET) ??
      this.registry.all()[0];
    if (ruleset === undefined) {
      throw new Error('No ruleset is registered under RULESETS.');
    }
    return ruleset;
  });

  constructor() {
    this.remember();
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      this.params.set(readParams(this.router.routerState.snapshot.root));
      this.remember();
    });
  }

  private known(id: string | null): Ruleset | undefined {
    return id === null ? undefined : this.registry.get(id);
  }

  private remember(): void {
    const ruleset = this.known(this.params().rulesetId);
    if (ruleset !== undefined && ruleset.id !== this.remembered()) {
      this.remembered.set(ruleset.id);
      this.storage.set(STORAGE_KEY, ruleset.id);
    }
  }
}
