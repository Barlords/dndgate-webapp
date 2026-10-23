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
import { RouterLink } from '@angular/router';
import { map, Subject } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  createSpellBook,
  getMySpellBooks,
  importSpellBookFile,
} from '../../../core/api/generated/functions';
import { SpellBookDto } from '../../../core/api/generated/models/spell-book-dto';
import { ApiFieldError, ApiProblem, toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';
import { Button } from '../../../shared/ui/button/button';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { LOADING, LoadState, loadWithRetry } from '../pages/load-state';
import { RulesetRegistry } from '../rulesets/ruleset-registry';
import { SelectedRulesetService } from '../rulesets/selected-ruleset.service';
import { SpellBookForm, SpellBookValue } from './spellbook-form';

const LIMIT_REACHED: ApiProblem = {
  status: 409,
  detail: 'tu as atteint la limite de 100 grimoires.',
  errors: [],
};

/** « Mes grimoires »: the signed-in reader's spell books, with creation and file import. */
@Component({
  selector: 'dg-spellbook-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, Spinner, Button, RouterLink, SpellBookForm],
  styles: `
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--dg-space-3);
      margin-bottom: var(--dg-space-4);
    }

    h1 {
      margin: 0;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-xl);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--dg-title-color);
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-3);
    }

    .file {
      display: none;
    }

    .create {
      margin-bottom: var(--dg-space-4);
    }

    .import-errors {
      margin: 0 0 var(--dg-space-4);
      padding: var(--dg-space-3) var(--dg-space-4);
      color: var(--dg-blood);
      border: 1px solid var(--dg-blood);
      border-radius: var(--dg-radius-sm);

      p {
        margin: 0 0 var(--dg-space-2);
        font-weight: 600;
      }

      ul {
        margin: 0;
        padding-left: var(--dg-space-4);
      }
    }

    .books {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
      gap: var(--dg-space-4);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .book {
      padding: var(--dg-space-4);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      background: var(--dg-vellum);
    }

    .book__name {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-md);
      letter-spacing: 0.08em;
      color: var(--dg-blood);
    }

    .book__meta {
      margin: var(--dg-space-1) 0 0;
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
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
      <header class="head">
        <h1>Mes grimoires</h1>
        <div class="actions">
          <dg-button (click)="toggleCreate()">Nouveau grimoire</dg-button>
          <dg-button variant="ghost" [loading]="importing()" (click)="file.click()">
            Importer
          </dg-button>
          <input
            #file
            class="file"
            type="file"
            accept="application/json,.json"
            aria-label="Fichier de grimoire à importer"
            (change)="importFile($event)"
          />
        </div>
      </header>

      @if (importErrors(); as errors) {
        <div class="import-errors" role="alert">
          <p>Ce fichier ne peut pas être importé.</p>
          <ul>
            @for (error of errors; track $index) {
              <li>
                @if (error.field) {
                  <strong>{{ error.field }}</strong> :
                }
                {{ error.message }}
              </li>
            }
          </ul>
        </div>
      }

      @if (creating()) {
        <section class="create" aria-label="Nouveau grimoire">
          <dg-spellbook-form
            [rulesets]="choices()"
            [initial]="blank()"
            [busy]="saving()"
            (saved)="create($event)"
            (cancelled)="creating.set(false)"
          />
        </section>
      }

      @if (loaded().value !== null) {
        @if (books().length > 0) {
          <ul class="books">
            @for (book of books(); track book.id) {
              <li class="book">
                <a class="book__name" [routerLink]="['/library/grimoires', book.id]">{{
                  book.name
                }}</a>
                <p class="book__meta">{{ rulesetLabel(book) }}</p>
                <p class="book__meta">{{ count(book) }}</p>
              </li>
            }
          </ul>
        } @else {
          <p>Tu n’as pas encore de grimoire.</p>
        }
      } @else if (loaded().failed) {
        <div class="state">
          <p role="alert">Impossible de charger tes grimoires.</p>
          <dg-button variant="ghost" (click)="retry()">Réessayer</dg-button>
        </div>
      } @else {
        <div class="state"><dg-spinner label="Chargement des grimoires" /></div>
      }
    </dg-parchment>
  `,
})
export class SpellBookListPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly registry = inject(RulesetRegistry);
  private readonly selected = inject(SelectedRulesetService);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly form = viewChild(SpellBookForm);

  private readonly retries = new Subject<void>();
  protected readonly loaded = signal<LoadState<SpellBookDto[]>>(LOADING);
  protected readonly books = signal<SpellBookDto[]>([]);
  protected readonly creating = signal(false);
  protected readonly saving = signal(false);
  protected readonly importing = signal(false);
  protected readonly importErrors = signal<ApiFieldError[] | null>(null);

  /** The rulesets that have spells, i.e. the ones a spell book can be bound to. */
  protected readonly choices = computed(() =>
    this.registry
      .all()
      .filter((ruleset) => ruleset.spellSectionId !== null)
      .map((ruleset) => ({ id: ruleset.id, label: ruleset.label })),
  );

  protected readonly blank = computed<SpellBookValue>(() => {
    const choices = this.choices();
    const current = this.selected.current().id;
    return {
      name: '',
      description: '',
      ruleset: choices.some((choice) => choice.id === current) ? current : (choices[0]?.id ?? ''),
    };
  });

  constructor() {
    loadWithRetry(
      () => getMySpellBooks(this.http, this.rootUrl).pipe(map((response) => response.body)),
      this.retries,
    )
      .pipe(takeUntilDestroyed())
      .subscribe((state) => {
        this.loaded.set(state);
        if (state.value !== null) {
          this.books.set(state.value);
        }
      });
  }

  protected rulesetLabel(book: SpellBookDto): string {
    return this.registry.get(book.ruleset ?? '')?.label ?? book.ruleset ?? '';
  }

  protected count(book: SpellBookDto): string {
    const n = book.spellList?.length ?? 0;
    return `${n} ${n > 1 ? 'sorts' : 'sort'}`;
  }

  protected retry(): void {
    this.retries.next();
  }

  protected toggleCreate(): void {
    this.creating.update((open) => !open);
  }

  protected create(value: SpellBookValue): void {
    this.saving.set(true);
    createSpellBook(this.http, this.rootUrl, { body: value })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.saving.set(false);
          this.creating.set(false);
          this.books.update((books) => [...books, body]);
          this.notifier.success('Grimoire créé.');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.notifier.error(LIMIT_REACHED);
          } else if (!(problem.status === 400 && this.form()?.showProblem(problem))) {
            this.notifier.error(problem, 'Création du grimoire');
          }
        },
      });
  }

  protected importFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.importErrors.set(null);
    this.importing.set(true);
    importSpellBookFile(this.http, this.rootUrl, { body: { file } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.importing.set(false);
          this.books.update((books) => [...books, body]);
          this.notifier.success('Grimoire importé.');
        },
        error: (error: unknown) => {
          this.importing.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.notifier.error(LIMIT_REACHED);
          } else if (problem.status === 400) {
            this.importErrors.set(
              problem.errors.length > 0
                ? problem.errors
                : [{ field: '', message: problem.detail ?? 'Le fichier est invalide.' }],
            );
          } else {
            this.notifier.error(problem, 'Import du grimoire');
          }
        },
      });
    // Lets the same file be chosen again after a fix.
    input.value = '';
  }
}
