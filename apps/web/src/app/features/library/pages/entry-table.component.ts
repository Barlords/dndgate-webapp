import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  linkedSignal,
  TemplateRef,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { SafeStorage } from '../../../core/storage/safe-storage';
import { IconButton } from '../../../shared/ui/icon-button/icon-button';
import { Paginator } from '../../../shared/ui/paginator/paginator';
import { FilterOption, FilterPanel, PAGE_SIZES } from './filter-panel.component';
import {
  ColumnDef,
  EntrySummary,
  FieldValue,
  FilterDef,
  Ruleset,
  RulesetSection,
} from '../rulesets/ruleset.model';
import { I18n } from '../../../core/i18n/i18n.service';

const COLLATOR = new Intl.Collator('fr', { numeric: true, sensitivity: 'base' });
let nextId = 0;

/** Lower case without accents, so that a search ignores both. */
function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

function value(entry: EntrySummary, key: string): FieldValue | undefined {
  return key === 'name' ? entry.name : entry.fields[key];
}

/** A field as the reader reads it; `yesNo` writes a boolean in their language. */
function display(field: FieldValue | undefined, yesNo: { yes: string; no: string }): string {
  if (field == null) {
    return '';
  }
  if (typeof field === 'boolean') {
    return field ? yesNo.yes : yesNo.no;
  }
  return Array.isArray(field) ? field.join(', ') : String(field);
}

function matches(field: FieldValue | undefined, option: string): boolean {
  return Array.isArray(field) ? field.includes(option) : String(field) === option;
}

/** Numbers and booleans by value, anything else by text; an entry without the field comes last. */
function compare(a: FieldValue | undefined, b: FieldValue | undefined): number {
  if (a === undefined || b === undefined) {
    return a === b ? 0 : a === undefined ? 1 : -1;
  }
  if (typeof a === 'boolean' && typeof b === 'boolean') {
    return Number(a) - Number(b);
  }
  return typeof a === 'number' && typeof b === 'number'
    ? a - b
    : COLLATOR.compare(display(a, YES_NO), display(b, YES_NO));
}

/** Booleans are compared by value: these words are never shown. */
const YES_NO = { yes: 'true', no: 'false' };

/**
 * The entries of a section as a table: search by name, sort by the sortable columns and
 * client-side pages; in the panel of its filter button (`FilterPanel`), a list of boxes per filter
 * of the section (any number ticked), the columns to show and the page size. Search, filters, sort and
 * page live in the query string of the page, so a link shares them; the columns shown are a
 * preference of the reader's browser, the same for every table of the section.
 *
 * `rowActions`, when given, fills a last column (the spell book buttons).
 */
@Component({
  selector: 'dg-entry-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FilterPanel, IconButton, NgTemplateOutlet, Paginator, RouterLink],
  templateUrl: './entry-table.component.html',
  styleUrl: './entry-table.component.scss',
  host: { '(document:keydown.escape)': 'closePanel()' },
})
export class EntryTable {
  protected readonly t = inject(I18n).t;

  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly storage = inject(SafeStorage);

  readonly ruleset = input.required<Ruleset>();
  readonly section = input.required<RulesetSection>();
  readonly entries = input.required<EntrySummary[]>();
  readonly rowActions = input<TemplateRef<{ $implicit: EntrySummary }> | null>(null);
  /** Read to assistive technologies as the heading of the actions column. */
  readonly actionsLabel = input<string | null>(null);

  protected readonly panelId = `dg-entry-filters-${nextId++}`;
  /** Closed again when the table shows another section (the page is kept between sections). */
  protected readonly panelOpen = linkedSignal({ source: this.section, computation: () => false });
  private readonly panelToggle = viewChild('panelToggle', { read: ElementRef });
  private readonly query = toSignal(this.route.queryParamMap, { requireSync: true });

  protected readonly search = computed(() => this.query().get('q') ?? '');

  /** The options of each filter: its own, or else the values the entries have. */
  protected readonly filterOptions = computed<Record<string, FilterOption[]>>(() =>
    Object.fromEntries(
      this.section().filters.map((filter) => [filter.key, filter.options ?? this.found(filter)]),
    ),
  );
  /** The options ticked per filter; a value of the URL that is not one of its options is ignored. */
  protected readonly ticked = computed<Record<string, string[]>>(() =>
    Object.fromEntries(
      this.section().filters.map((filter) => {
        const known = this.filterOptions()[filter.key].map((option) => option.value);
        return [
          filter.key,
          this.query()
            .getAll(filter.key)
            .filter((v) => known.includes(v)),
        ];
      }),
    ),
  );
  protected readonly tickedCount = computed(() =>
    Object.values(this.ticked()).reduce((count, values) => count + values.length, 0),
  );

  private readonly storageKey = computed(
    () => `dg.columns.${this.ruleset().id}.${this.section().id}`,
  );
  /** The keys of the columns the reader hid, read again when the section changes. */
  protected readonly hidden = linkedSignal<ReadonlySet<string>>(() => {
    try {
      return new Set(JSON.parse(this.storage.get(this.storageKey()) ?? '[]') as string[]);
    } catch {
      return new Set();
    }
  });
  /** The first column holds the link to the entry: it is always shown. */
  protected readonly columns = computed(() =>
    this.section().columns.filter((column, i) => i === 0 || !this.hidden().has(column.key)),
  );

  /** The column of the URL, or else the section's default one (the name unless it says). */
  protected readonly sort = computed(() => {
    const sortable = (key: string | null) =>
      this.section().columns.find((candidate) => candidate.sortable && candidate.key === key);
    const asked = sortable(this.query().get('sort'));
    if (asked !== undefined) {
      return { key: asked.key, desc: this.query().get('dir') === 'desc' };
    }
    const fallback = sortable(this.section().defaultSort ?? 'name');
    return fallback === undefined ? null : { key: fallback.key, desc: false };
  });
  protected readonly pageSize = computed(() => {
    const size = Number(this.query().get('size'));
    return PAGE_SIZES.includes(size) ? size : PAGE_SIZES[0];
  });

  protected readonly shown = computed(() => {
    const search = fold(this.search());
    const filters = Object.entries(this.ticked()).filter(([, options]) => options.length > 0);
    const shown = this.entries().filter(
      (entry) =>
        fold(entry.name).includes(search) &&
        filters.every(([key, options]) =>
          options.some((option) => matches(entry.fields[key], option)),
        ),
    );
    const sort = this.sort();
    const sign = sort?.desc ? -1 : 1;
    // Entries that tie on the sorted column stay in alphabetical order.
    shown.sort(
      (a, b) =>
        (sort === null ? 0 : sign * compare(value(a, sort.key), value(b, sort.key))) ||
        COLLATOR.compare(a.name, b.name),
    );
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

  protected cell(entry: EntrySummary, column: ColumnDef): string {
    const shown = value(entry, column.key);
    return shown != null && column.format
      ? column.format(shown)
      : display(shown, this.t().common.actions);
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

  protected togglePanel(): void {
    this.panelOpen.update((open) => !open);
  }

  /** Closes the panel, giving the focus back to the button that opened it. */
  protected closePanel(): void {
    if (!this.panelOpen()) {
      return;
    }
    this.panelOpen.set(false);
    (this.panelToggle()?.nativeElement as HTMLElement | undefined)
      ?.querySelector('button')
      ?.focus();
  }

  protected searchFor(text: string): void {
    this.update({ q: text || null, page: null }, true);
  }

  protected tick(key: string, option: string, on: boolean): void {
    const others = this.ticked()[key].filter((ticked) => ticked !== option);
    const values = on ? [...others, option] : others;
    this.update({ [key]: values.length > 0 ? values : null, page: null });
  }

  protected clearFilters(): void {
    this.update({
      ...Object.fromEntries(this.section().filters.map((filter) => [filter.key, null])),
      page: null,
    });
  }

  protected showColumn(column: ColumnDef, on: boolean): void {
    const hidden = new Set(this.hidden());
    if (on) {
      hidden.delete(column.key);
    } else {
      hidden.add(column.key);
    }
    this.hidden.set(hidden);
    if (hidden.size === 0) {
      this.storage.remove(this.storageKey());
    } else {
      this.storage.set(this.storageKey(), JSON.stringify([...hidden]));
    }
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

  /** The values the entries have for the filter's field, lists flattened, in alphabetical order. */
  private found(filter: FilterDef): FilterOption[] {
    const values = new Set<string>();
    for (const entry of this.entries()) {
      const field = entry.fields[filter.key];
      for (const one of Array.isArray(field) ? field : field == null ? [] : [field]) {
        values.add(String(one));
      }
    }
    return [...values].sort(COLLATOR.compare).map((v) => ({ value: v, label: v }));
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
