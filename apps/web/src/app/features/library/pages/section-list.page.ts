import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { map, Subject, switchMap } from 'rxjs';
import { Button } from '../../../shared/ui/button/button';
import { Paginator } from '../../../shared/ui/paginator/paginator';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { RulesetRegistry } from '../rulesets/ruleset-registry';
import {
  ColumnDef,
  EntrySummary,
  FieldValue,
  Ruleset,
  RulesetSection,
} from '../rulesets/ruleset.model';
import { LOADING, LoadState, loadWithRetry } from './load-state';

const PAGE_SIZES = [25, 50, 100];
const COLLATOR = new Intl.Collator('fr', { numeric: true, sensitivity: 'base' });

/** Lower case without accents, so that a search ignores both. */
function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

function value(entry: EntrySummary, key: string): FieldValue | undefined {
  return key === 'name' ? entry.name : entry.fields[key];
}

function display(field: FieldValue | undefined): string {
  if (field === undefined) {
    return '';
  }
  if (typeof field === 'boolean') {
    return field ? 'Oui' : 'Non';
  }
  return Array.isArray(field) ? field.join(', ') : String(field);
}

function matches(field: FieldValue | undefined, option: string): boolean {
  return Array.isArray(field) ? field.includes(option) : String(field) === option;
}

/** Numbers by value, anything else by text; an entry without the field comes last. */
function compare(a: FieldValue | undefined, b: FieldValue | undefined): number {
  if (a === undefined || b === undefined) {
    return a === b ? 0 : a === undefined ? 1 : -1;
  }
  return typeof a === 'number' && typeof b === 'number'
    ? a - b
    : COLLATOR.compare(display(a), display(b));
}

/**
 * The entries of one section: search by name, a select per filter of the section, sort by the
 * sortable columns and client-side pages. All of it lives in the query string, so a link shares it.
 */
@Component({
  selector: 'dg-section-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, RouterLink, Spinner, Button, Paginator],
  templateUrl: './section-list.page.html',
  styleUrl: './section-list.page.scss',
})
export class SectionListPage {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly registry = inject(RulesetRegistry);

  protected readonly pageSizes = PAGE_SIZES;
  private readonly retries = new Subject<void>();
  private readonly loaded = signal<LoadState<EntrySummary[]>>(LOADING);
  protected readonly entries = computed(() => this.loaded().value);
  protected readonly failed = computed(() => this.loaded().failed);

  private readonly params = toSignal(this.route.paramMap, { requireSync: true });
  private readonly query = toSignal(this.route.queryParamMap, { requireSync: true });

  // The routes only match a registered ruleset and one of its sections.
  protected readonly ruleset = computed<Ruleset>(() =>
    this.registry.get(this.params().get('rulesetId') ?? '')!,
  );
  protected readonly section = computed<RulesetSection>(() =>
    this.ruleset().sections.find((section) => section.id === this.params().get('sectionId'))!,
  );

  protected readonly search = computed(() => this.query().get('q') ?? '');
  /** The option chosen per filter; a value of the URL that is not one of its options is ignored. */
  protected readonly filterValues = computed<Record<string, string>>(() =>
    Object.fromEntries(
      this.section().filters.map((filter) => {
        const chosen = this.query().get(filter.key) ?? '';
        const known = filter.options.some((option) => option.value === chosen);
        return [filter.key, known ? chosen : ''];
      }),
    ),
  );
  protected readonly sort = computed(() => {
    const key = this.query().get('sort');
    const column = this.section().columns.find((candidate) => candidate.key === key);
    return column?.sortable ? { key: column.key, desc: this.query().get('dir') === 'desc' } : null;
  });
  protected readonly pageSize = computed(() => {
    const size = Number(this.query().get('size'));
    return PAGE_SIZES.includes(size) ? size : PAGE_SIZES[0];
  });

  protected readonly shown = computed(() => {
    const search = fold(this.search());
    const filters = Object.entries(this.filterValues()).filter(([, option]) => option !== '');
    const shown = (this.entries() ?? []).filter(
      (entry) =>
        fold(entry.name).includes(search) &&
        filters.every(([key, option]) => matches(entry.fields[key], option)),
    );
    const sort = this.sort();
    if (sort !== null) {
      const sign = sort.desc ? -1 : 1;
      shown.sort((a, b) => sign * compare(value(a, sort.key), value(b, sort.key)));
    }
    return shown;
  });
  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.shown().length / this.pageSize())),
  );
  /** The page shown, from 0; the URL counts from 1. */
  protected readonly page = computed(() => {
    const asked = Math.floor(Number(this.query().get('page'))) || 1;
    return Math.min(Math.max(asked, 1), this.totalPages()) - 1;
  });
  protected readonly rows = computed(() => {
    const start = this.page() * this.pageSize();
    return this.shown().slice(start, start + this.pageSize());
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

  protected cell(entry: EntrySummary, column: ColumnDef): string {
    return display(value(entry, column.key));
  }

  protected ariaSort(column: ColumnDef): string | null {
    if (!column.sortable) {
      return null;
    }
    const sort = this.sort();
    if (sort?.key !== column.key) {
      return 'none';
    }
    return sort.desc ? 'descending' : 'ascending';
  }

  protected link(entry: EntrySummary): string[] {
    return ['/library', this.ruleset().id, this.section().id, entry.index];
  }

  protected searchFor(text: string): void {
    this.update({ q: text || null, page: null }, true);
  }

  protected filterBy(key: string, option: string): void {
    this.update({ [key]: option || null, page: null });
  }

  protected sortBy(column: ColumnDef): void {
    const sort = this.sort();
    const desc = sort?.key === column.key && !sort.desc;
    this.update({ sort: column.key, dir: desc ? 'desc' : 'asc', page: null });
  }

  protected resize(size: string): void {
    this.update({ size: Number(size) === PAGE_SIZES[0] ? null : size, page: null });
  }

  protected goTo(page: number): void {
    this.update({ page: page === 0 ? null : page + 1 });
  }

  private update(params: Params, replaceUrl = false): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
      replaceUrl,
    });
  }
}
