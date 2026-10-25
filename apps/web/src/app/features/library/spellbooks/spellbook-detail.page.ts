import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, EMPTY, map, Observable, of, skip, Subject, switchMap } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  deleteSpellBookById,
  exportSpellBookFile,
  getSpellBookById,
  patchSpellBook,
  removeSpellFromBook,
} from '../../../core/api/generated/functions';
import { SpellBookDto } from '../../../core/api/generated/models/spell-book-dto';
import { SpellBookPatchRequest } from '../../../core/api/generated/models/spell-book-patch-request';
import { toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import { Icon } from '../../../shared/ui/icon/icon';
import { IconButton } from '../../../shared/ui/icon-button/icon-button';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { Loading } from '../../../shared/ui/loading/loading';
import { LOADING, LoadState, loadWithRetry } from '../pages/load-state';
import { RulesetRegistry } from '../rulesets/ruleset-registry';
import { EntryTable } from '../pages/entry-table.component';
import { EntrySummary, Ruleset, RulesetSection } from '../rulesets/ruleset.model';
import { SpellBookForm, SpellBookValue } from './spellbook-form';
import { downloadSpellBook } from './spellbook-file';
import { I18n } from '../../../core/i18n/i18n.service';

/** What the book's ruleset tells about its spells: their summaries, or why nothing is known. */
interface Catalog {
  entries: Map<string, EntrySummary> | null;
  /** The ruleset and its spell section, whose table shows the spells. */
  ruleset: Ruleset | null;
  section: RulesetSection | null;
  /** Why the spells cannot be shown in full, as a text of the dictionary. */
  note: 'noRuleset' | 'noDetails' | null;
}

interface Loaded {
  book: SpellBookDto;
  catalog: Catalog;
}

const NO_RULESET: Catalog = {
  entries: null,
  ruleset: null,
  section: null,
  note: 'noRuleset',
};
const NO_DETAILS: Catalog = {
  ...NO_RULESET,
  note: 'noDetails',
};

/**
 * One spell book: its name, its actions and its description, then its spells in the table of the
 * book's ruleset (the one of the Spells menu), each with a button to take it out of the book.
 * Spells the ruleset does not know, or all of them when it cannot be read, are listed below by
 * index, so that they can still be removed.
 */
@Component({
  selector: 'dg-spellbook-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Parchment, Loading, Icon, IconButton, RouterLink, SpellBookForm, EntryTable],
  styles: `
    // The way back at the start, the name in the middle, the actions at the end of the line.
    .head {
      display: grid;
      grid-template-columns: 1fr auto 1fr;
      align-items: center;
      gap: var(--dg-space-3);
      margin-bottom: var(--dg-space-4);
    }

    .head__name {
      text-align: center;
    }

    .back {
      display: inline-flex;
      align-items: center;
      gap: var(--dg-space-1);
      justify-self: start;
      padding: var(--dg-space-1) var(--dg-space-3) var(--dg-space-1) var(--dg-space-2);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.08em;
      white-space: nowrap;
      color: var(--dg-ink);
      text-decoration: none;
      border: 1px solid color-mix(in srgb, currentColor 40%, transparent);
      border-radius: var(--dg-radius);
      transition:
        background var(--dg-transition),
        border-color var(--dg-transition);

      &:hover {
        background: color-mix(in srgb, var(--dg-parchment-border) 20%, transparent);
        border-color: currentColor;
      }
    }

    h1 {
      margin: 0;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-2xl);
      letter-spacing: 0.12em;
      color: var(--dg-title-color);
    }

    .ruleset {
      margin: var(--dg-space-1) 0 0;
      font-style: italic;
      color: var(--dg-ink-muted);
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--dg-space-2);
    }

    @media (max-width: 767.98px) {
      .head {
        grid-template-columns: 1fr;
      }

      .actions {
        justify-content: center;
      }
    }

    // The description reads as a quotation of the book: set apart by a rule, in italics.
    .description {
      margin: 0 0 var(--dg-space-5);
      padding: var(--dg-space-2) var(--dg-space-4);
      font-style: italic;
      white-space: pre-line;
      background: color-mix(in srgb, var(--dg-parchment-border) 10%, transparent);
      border-left: 3px solid var(--dg-parchment-border);
      border-radius: 0 var(--dg-radius-sm) var(--dg-radius-sm) 0;
    }

    .description__caption {
      display: block;
      margin-bottom: var(--dg-space-1);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      font-style: normal;
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--dg-gold-ink);
    }

    .note {
      padding: var(--dg-space-2) var(--dg-space-3);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius-sm);
      font-style: italic;
    }

    .unknown__title {
      margin: var(--dg-space-5) 0 var(--dg-space-2);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-md);
      letter-spacing: 0.12em;
      color: var(--dg-title-color);
    }

    .unknown {
      margin: var(--dg-space-3) 0 0;
      padding: 0;
      list-style: none;
    }

    .spell {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--dg-space-3);
      padding: var(--dg-space-2) 0;
      border-bottom: 1px solid var(--dg-parchment-shadow);
    }

    .spell__name--unknown {
      font-style: italic;
      color: var(--dg-blood);
    }

    .state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dg-space-3);
      margin: var(--dg-space-6) 0;
      text-align: center;

      p {
        margin: 0;
        color: var(--dg-blood);
        font-weight: 600;
      }
    }
  `,
  template: `
    <dg-parchment>
      @if (book(); as shown) {
        <article>
          @if (editing()) {
            <dg-spellbook-form
              [submitLabel]="t().common.actions.save"
              [initial]="editValue()"
              [busy]="saving()"
              (saved)="save($event)"
              (cancelled)="editing.set(false)"
            />
          }
          <header class="head">
            <a class="back" routerLink="/library/grimoires">
              <dg-icon name="chevron-left" />
              {{ t().library.spellbooks.back }}
            </a>
            <div class="head__name">
              <h1>{{ shown.name }}</h1>
              <p class="ruleset">{{ rulesetLabel() }}</p>
            </div>
            <div class="actions">
              <dg-icon-button
                icon="edit"
                [label]="t().common.actions.edit"
                (click)="editing.set(true)"
              />
              <dg-icon-button
                icon="download"
                [label]="t().library.spellbooks.export"
                [loading]="exporting()"
                (click)="export()"
              />
              <dg-icon-button
                icon="trash"
                [label]="t().common.actions.delete"
                variant="danger"
                (click)="remove()"
              />
            </div>
          </header>
          @if (shown.description) {
            <p class="description">
              <span class="description__caption">{{ t().library.spellbooks.description }}</span
              >{{ shown.description }}
            </p>
          }

          @if (catalog().note; as note) {
            <p class="note" role="status">{{ t().library.spellbooks[note] }}</p>
          }
          @if ((shown.spellList ?? []).length === 0) {
            <p>{{ t().library.spellbooks.empty }}</p>
          } @else {
            @if (catalog().ruleset && catalog().section && known().length > 0) {
              <dg-entry-table
                [ruleset]="catalog().ruleset!"
                [section]="catalog().section!"
                [entries]="known()"
                [rowActions]="removeAction"
                [actionsLabel]="t().library.spellbooks.remove"
              />
            }
            @if (unknown().length > 0) {
              @if (catalog().entries) {
                <h2 class="unknown__title">{{ t().library.spellbooks.unknownTitle }}</h2>
              }
              <ul class="unknown">
                @for (index of unknown(); track index) {
                  <li class="spell">
                    <span [class.spell__name--unknown]="catalog().entries">{{
                      catalog().entries ? t().library.spellbooks.unknownSpell(index) : index
                    }}</span>
                    <dg-icon-button
                      icon="trash"
                      variant="danger"
                      [label]="t().library.spellbooks.remove"
                      [ariaLabel]="t().library.spellbooks.removeSpell(index)"
                      (click)="removeSpell(index)"
                    />
                  </li>
                }
              </ul>
            }
          }
        </article>
      } @else if (notFound()) {
        <div class="state">
          <p role="alert">{{ t().library.spellbooks.notFound }}</p>
          <a routerLink="/library/grimoires">{{ t().library.spellbooks.back }}</a>
        </div>
      } @else if (loaded().failed) {
        <div class="state">
          <dg-load-error
            [message]="t().library.spellbooks.bookLoadError"
            [problem]="loadProblem()"
            (retry)="reload()"
          />
        </div>
      } @else {
        <div class="state">
          <dg-loading
            [label]="t().library.spellbooks.bookLoading"
            [message]="t().library.spellbooks.bookLoadingFlavour"
          />
        </div>
      }
    </dg-parchment>

    <ng-template #removeAction let-entry>
      <dg-icon-button
        icon="trash"
        variant="danger"
        [label]="t().library.spellbooks.remove"
        [ariaLabel]="t().library.spellbooks.removeSpell(entry.name)"
        (click)="removeSpell(entry.index)"
      />
    </ng-template>
  `,
})
export class SpellBookDetailPage {
  private readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;

  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly registry = inject(RulesetRegistry);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifier = inject(NotifierService);
  private readonly confirm = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly form = viewChild(SpellBookForm);
  private readonly retries = new Subject<void>();

  protected readonly loaded = signal<LoadState<Loaded>>(LOADING);
  protected readonly loadProblem = computed(() => toApiProblem(this.loaded().error));
  protected readonly book = signal<SpellBookDto | null>(null);
  protected readonly catalog = signal<Catalog>(NO_RULESET);
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly exporting = signal(false);

  protected readonly notFound = computed(() => {
    const error = this.loaded().error;
    return this.loaded().failed && toApiProblem(error).status === 404;
  });

  protected readonly rulesetLabel = computed(() => {
    const id = this.book()?.ruleset ?? '';
    return this.registry.get(id)?.label ?? id;
  });

  protected readonly editValue = computed<SpellBookValue>(() => ({
    name: this.book()?.name ?? '',
    description: this.book()?.description ?? '',
    ruleset: this.book()?.ruleset ?? '',
  }));

  /** The spells of the book the ruleset knows, for the table. */
  protected readonly known = computed<EntrySummary[]>(() => {
    const { entries } = this.catalog();
    return entries === null
      ? []
      : (this.book()?.spellList ?? []).flatMap((index) => entries.get(index) ?? []);
  });
  /** The others (all of them when the ruleset cannot be read), by index. */
  protected readonly unknown = computed<string[]>(() => {
    const { entries } = this.catalog();
    return (this.book()?.spellList ?? [])
      .filter((index) => entries === null || !entries.has(index))
      .sort();
  });

  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => params.get('id') ?? ''),
        switchMap((id) => loadWithRetry(() => this.load(id), this.retries)),
        takeUntilDestroyed(),
      )
      .subscribe((state) => {
        this.loaded.set(state);
        if (state.value !== null) {
          this.book.set(state.value.book);
          this.catalog.set(state.value.catalog);
          this.breadcrumb.setLast(state.value.book.name ?? '');
        }
      });
    // The spells come in the reader's language: read them again when it changes (the book stays).
    toObservable(this.i18n.language)
      .pipe(
        skip(1),
        switchMap(() => {
          const book = this.book();
          return book === null ? EMPTY : this.catalogOf(book);
        }),
        takeUntilDestroyed(),
      )
      .subscribe((catalog) => this.catalog.set(catalog));
  }

  protected reload(): void {
    this.editing.set(false);
    this.retries.next();
  }

  private load(id: string): Observable<Loaded> {
    return getSpellBookById(this.http, this.rootUrl, { id }).pipe(
      switchMap(({ body: book }) =>
        this.catalogOf(book).pipe(map((catalog) => ({ book, catalog }))),
      ),
    );
  }

  /** Reads the spells of the ruleset the book is bound to (not the one the reader browses). */
  private catalogOf(book: SpellBookDto): Observable<Catalog> {
    const ruleset = this.registry.get(book.ruleset ?? '');
    const section = ruleset?.sections.find((s) => s.id === ruleset.spellSectionId);
    if (ruleset === undefined || section === undefined) {
      return of(NO_RULESET);
    }
    return ruleset.list(section.id).pipe(
      map((entries) => ({
        entries: new Map(entries.map((entry) => [entry.index, entry])),
        ruleset,
        section,
        note: null,
      })),
      catchError(() => of(NO_DETAILS)),
    );
  }

  protected save(value: SpellBookValue): void {
    const book = this.book()!;
    const body: SpellBookPatchRequest = {};
    if (value.name !== book.name) {
      body.name = value.name;
    }
    if (value.description !== book.description) {
      body.description = value.description;
    }
    if (body.name === undefined && body.description === undefined) {
      this.editing.set(false);
      return;
    }
    this.saving.set(true);
    patchSpellBook(this.http, this.rootUrl, { id: book.id!, body })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body: updated }) => {
          this.saving.set(false);
          this.editing.set(false);
          this.book.set(updated);
          this.breadcrumb.setLast(updated.name ?? '');
          this.notifier.success(this.t().library.spellbooks.updated);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.notifier.conflict(() => this.reload());
          } else if (!(problem.status === 400 && this.form()?.showProblem(problem))) {
            this.notifier.error(problem, this.t().library.spellbooks.updating);
          }
        },
      });
  }

  protected removeSpell(spellIndex: string): void {
    removeSpellFromBook(this.http, this.rootUrl, { id: this.book()!.id!, spellIndex })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () =>
          this.book.update((book) => ({
            ...book!,
            spellList: (book!.spellList ?? []).filter((index) => index !== spellIndex),
          })),
        error: (error: unknown) =>
          this.notifier.error(toApiProblem(error), this.t().library.spellbooks.removingSpell),
      });
  }

  protected export(): void {
    const book = this.book()!;
    this.exporting.set(true);
    exportSpellBookFile(this.http, this.rootUrl, { id: book.id! })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.exporting.set(false);
          downloadSpellBook(body, book.name ?? '');
        },
        error: (error: unknown) => {
          this.exporting.set(false);
          this.notifier.error(toApiProblem(error), this.t().library.spellbooks.exporting);
        },
      });
  }

  protected remove(): void {
    const book = this.book()!;
    this.confirm
      .typeToConfirm({
        title: this.t().library.spellbooks.deleteTitle,
        message: this.t().library.spellbooks.deleteMessage(book.name ?? ''),
        expected: book.name,
        inputLabel: this.t().library.spellbooks.deleteInput,
        inputType: 'text',
        confirmLabel: this.t().common.actions.delete,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap((confirmation) =>
          confirmation === null
            ? of(null)
            : deleteSpellBookById(this.http, this.rootUrl, {
                id: book.id!,
                body: { confirmation },
              }).pipe(map(() => true)),
        ),
      )
      .subscribe({
        next: (done) => {
          if (done) {
            this.notifier.success(this.t().library.spellbooks.deleted);
            void this.router.navigateByUrl('/library/grimoires');
          }
        },
        error: (error: unknown) =>
          this.notifier.error(toApiProblem(error), this.t().library.spellbooks.deleting),
      });
  }
}
