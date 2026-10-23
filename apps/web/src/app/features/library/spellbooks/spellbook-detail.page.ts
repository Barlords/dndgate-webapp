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
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, map, Observable, of, Subject, switchMap } from 'rxjs';
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
import { Button } from '../../../shared/ui/button/button';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { LOADING, LoadState, loadWithRetry } from '../pages/load-state';
import { RulesetRegistry } from '../rulesets/ruleset-registry';
import { EntrySummary } from '../rulesets/ruleset.model';
import { SpellBookForm, SpellBookValue } from './spellbook-form';
import { downloadSpellBook } from './spellbook-file';

/** What the book's ruleset tells about its spells: their summaries, or why nothing is known. */
interface Catalog {
  entries: Map<string, EntrySummary> | null;
  /** Where a spell page lives in the ruleset (`/library/<ruleset>/<spells section>`). */
  path: string[] | null;
  note: string | null;
}

interface SpellRow {
  index: string;
  label: string;
  level: number | null;
  school: string;
  link: string[] | null;
  unknown: boolean;
}

interface Loaded {
  book: SpellBookDto;
  catalog: Catalog;
}

const NO_RULESET: Catalog = { entries: null, path: null, note: 'Jeu de règles indisponible.' };
const NO_DETAILS: Catalog = {
  entries: null,
  path: null,
  note: 'Détails des sorts indisponibles pour le moment.',
};

function levelLabel(level: number | null): string {
  if (level === null) {
    return '';
  }
  return level === 0 ? 'Tour de magie' : `Niveau ${level}`;
}

/** One spell book: its name and description, and its spells as the book's ruleset describes them. */
@Component({
  selector: 'dg-spellbook-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Parchment, Spinner, Button, RouterLink, SpellBookForm],
  styles: `
    .head {
      text-align: center;
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

    .description {
      white-space: pre-line;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: var(--dg-space-3);
      margin: var(--dg-space-4) 0;
    }

    .note {
      padding: var(--dg-space-2) var(--dg-space-3);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius-sm);
      font-style: italic;
    }

    .spells {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .spell {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--dg-space-3);
      padding: var(--dg-space-2) 0;
      border-bottom: 1px solid var(--dg-parchment-shadow);
    }

    .spell__name {
      flex: 1 1 12rem;
      font-weight: 600;
    }

    .spell__name--unknown {
      font-style: italic;
      color: var(--dg-blood);
    }

    .spell__meta {
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
      white-space: nowrap;
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
              submitLabel="Enregistrer"
              [initial]="editValue()"
              [busy]="saving()"
              (saved)="save($event)"
              (cancelled)="editing.set(false)"
            />
          }
          <header class="head">
            <h1>{{ shown.name }}</h1>
            <p class="ruleset">{{ rulesetLabel() }}</p>
          </header>
          <p class="description">{{ shown.description }}</p>
          <div class="actions">
            <dg-button variant="ghost" (click)="editing.set(true)">Modifier</dg-button>
            <dg-button variant="ghost" [loading]="exporting()" (click)="export()">
              Exporter
            </dg-button>
            <dg-button variant="danger" (click)="remove()">Supprimer</dg-button>
          </div>

          @if (catalog().note; as note) {
            <p class="note" role="status">{{ note }}</p>
          }
          @if (rows().length > 0) {
            <ul class="spells">
              @for (row of rows(); track row.index) {
                <li class="spell">
                  @if (row.link) {
                    <a class="spell__name" [routerLink]="row.link">{{ row.label }}</a>
                  } @else {
                    <span class="spell__name" [class.spell__name--unknown]="row.unknown">{{
                      row.label
                    }}</span>
                  }
                  @if (row.level !== null) {
                    <span class="spell__meta">{{ levelText(row.level) }}</span>
                  }
                  @if (row.school) {
                    <span class="spell__meta">{{ row.school }}</span>
                  }
                  <dg-button variant="ghost" (click)="removeSpell(row.index)">
                    Retirer<span class="sr-only"> {{ row.index }}</span>
                  </dg-button>
                </li>
              }
            </ul>
          } @else {
            <p>Ce grimoire ne contient aucun sort. Ajoute-en depuis la fiche d’un sort.</p>
          }
          <p><a routerLink="/library/grimoires">Retour à mes grimoires</a></p>
        </article>
      } @else if (notFound()) {
        <div class="state">
          <p role="alert">Grimoire introuvable.</p>
          <a routerLink="/library/grimoires">Retour à mes grimoires</a>
        </div>
      } @else if (loaded().failed) {
        <div class="state">
          <dg-load-error
            message="Impossible de charger ce grimoire."
            [problem]="loadProblem()"
            (retry)="reload()"
          />
        </div>
      } @else {
        <div class="state"><dg-spinner label="Chargement du grimoire" /></div>
      }
    </dg-parchment>
  `,
})
export class SpellBookDetailPage {
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

  /** Known spells by level then name, then the unknown ones by index. */
  protected readonly rows = computed<SpellRow[]>(() => {
    const { entries, path } = this.catalog();
    const rows = (this.book()?.spellList ?? []).map((index): SpellRow => {
      if (entries === null) {
        return { index, label: index, level: null, school: '', link: null, unknown: false };
      }
      const entry = entries.get(index);
      if (entry === undefined || path === null) {
        return {
          index,
          label: `Sort introuvable (${index})`,
          level: null,
          school: '',
          link: null,
          unknown: true,
        };
      }
      const level = Number(entry.fields['level']);
      return {
        index,
        label: entry.name,
        level: Number.isFinite(level) ? level : null,
        school: String(entry.fields['school'] ?? ''),
        link: [...path, index],
        unknown: false,
      };
    });
    return rows.sort(
      (a, b) =>
        Number(a.unknown) - Number(b.unknown) ||
        (a.level ?? 0) - (b.level ?? 0) ||
        a.label.localeCompare(b.label),
    );
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
  }

  protected levelText(level: number): string {
    return levelLabel(level);
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
    const sectionId = ruleset?.spellSectionId ?? null;
    if (ruleset === undefined || sectionId === null) {
      return of(NO_RULESET);
    }
    return ruleset.list(sectionId).pipe(
      map((entries) => ({
        entries: new Map(entries.map((entry) => [entry.index, entry])),
        path: ['/library', ruleset.id, sectionId],
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
          this.notifier.success('Grimoire mis à jour.');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.notifier.conflict(() => this.reload());
          } else if (!(problem.status === 400 && this.form()?.showProblem(problem))) {
            this.notifier.error(problem, 'Modification du grimoire');
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
        error: (error: unknown) => this.notifier.error(toApiProblem(error), 'Retrait du sort'),
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
          this.notifier.error(toApiProblem(error), 'Export du grimoire');
        },
      });
  }

  protected remove(): void {
    const book = this.book()!;
    this.confirm
      .typeToConfirm({
        title: 'Supprimer le grimoire',
        message: `Cette action est irréversible. Retape le nom du grimoire (${book.name}) pour confirmer.`,
        expected: book.name,
        inputLabel: 'Nom du grimoire',
        inputType: 'text',
        confirmLabel: 'Supprimer',
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
            this.notifier.success('Grimoire supprimé.');
            void this.router.navigateByUrl('/library/grimoires');
          }
        },
        error: (error: unknown) =>
          this.notifier.error(toApiProblem(error), 'Suppression du grimoire'),
      });
  }
}
