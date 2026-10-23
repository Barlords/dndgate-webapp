import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, RouterLink } from '@angular/router';
import { map, Subject, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { toApiProblem } from '../../../core/errors/api-problem';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { DEFAULT_LOADING_MESSAGE, Loading } from '../../../shared/ui/loading/loading';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { RulesetRegistry } from '../rulesets/ruleset-registry';
import { EntrySummary, Ruleset, RulesetSection } from '../rulesets/ruleset.model';
import { AddToSpellbook } from '../spellbooks/add-to-spellbook.component';
import { MySpellBooksStore } from '../spellbooks/my-spellbooks.store';
import { EntryTable } from './entry-table.component';
import { LOADING, LoadState, loadWithRetry } from './load-state';

/**
 * The entries of one section: its tabs, when it has some, over the table of the entries (search,
 * filters, sort, pages; see `EntryTable`). On a spell list, each row of a signed-in reader gets a
 * button to add the spell to one of their spell books.
 */
@Component({
  selector: 'dg-section-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Loading, Parchment, RouterLink, EntryTable, AddToSpellbook],
  // One read of the reader's spell books per visit, shared by the buttons of every row.
  providers: [MySpellBooksStore],
  templateUrl: './section-list.page.html',
  styleUrl: './section-list.page.scss',
})
export class SectionListPage {
  private readonly route = inject(ActivatedRoute);
  private readonly registry = inject(RulesetRegistry);
  private readonly auth = inject(AuthService);

  private readonly retries = new Subject<void>();
  private readonly loaded = signal<LoadState<EntrySummary[]>>(LOADING);
  protected readonly entries = computed(() => this.loaded().value);
  protected readonly failed = computed(() => this.loaded().failed);
  protected readonly loadProblem = computed(() => toApiProblem(this.loaded().error));

  private readonly params = toSignal(this.route.paramMap, { requireSync: true });
  private readonly query = toSignal(this.route.queryParamMap, { requireSync: true });

  // The routes only match a registered ruleset and one of its sections.
  protected readonly ruleset = computed<Ruleset>(() =>
    this.registry.get(this.params().get('rulesetId') ?? '')!,
  );
  protected readonly section = computed<RulesetSection>(() =>
    this.ruleset().sections.find((section) => section.id === this.params().get('sectionId'))!,
  );
  /** Each row of a spell list gets a button to add the spell to the reader's spell books. */
  protected readonly bookable = computed(
    () => this.auth.isAuthenticated() && this.section().id === this.ruleset().spellSectionId,
  );
  protected readonly loadingMessage = computed(
    () => this.section().loadingMessage ?? DEFAULT_LOADING_MESSAGE,
  );

  /** The tab shown, when the section has tabs; a value of the URL that is not one is ignored. */
  protected readonly tab = computed(() => {
    const tabs = this.section().tabs;
    if (tabs === undefined) {
      return null;
    }
    const chosen = this.query().get(tabs.key);
    return tabs.options.find((option) => option.value === chosen)?.value ?? tabs.options[0].value;
  });
  /** The entries of the tab shown, or all of them. */
  protected readonly tabEntries = computed(() => {
    const tabs = this.section().tabs;
    const entries = this.entries() ?? [];
    return tabs === undefined
      ? entries
      : entries.filter((entry) => entry.fields[tabs.key] === this.tab());
  });

  constructor() {
    // Loaded once per (ruleset, section): the query string only filters what is already here.
    this.route.paramMap
      .pipe(
        map((params) => [params.get('rulesetId') ?? '', params.get('sectionId') ?? ''] as const),
        switchMap(([rulesetId, sectionId]) =>
          loadWithRetry(() => this.registry.get(rulesetId)!.list(sectionId), this.retries),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((loaded) => this.loaded.set(loaded));
  }

  protected retry(): void {
    this.retries.next();
  }

  /** The query of a tab's link; the first tab, shown by default, keeps the URL clean. */
  protected tabQuery(option: string): Params {
    const tabs = this.section().tabs!;
    return { [tabs.key]: option === tabs.options[0].value ? null : option, page: null };
  }
}
