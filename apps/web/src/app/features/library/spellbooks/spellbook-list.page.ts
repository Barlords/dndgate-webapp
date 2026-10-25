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
import { ActivatedRoute, RouterLink } from '@angular/router';
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
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { Loading } from '../../../shared/ui/loading/loading';
import { ViewMode } from '../../../shared/ui/view-toggle/view-toggle';
import { ListHeader } from '../../tavern/list-header';
import { LOADING, LoadState, loadWithRetry } from '../pages/load-state';
import { RulesetRegistry } from '../rulesets/ruleset-registry';
import { SelectedRulesetService } from '../rulesets/selected-ruleset.service';
import { SpellBookForm, SpellBookValue } from './spellbook-form';
import { I18n } from '../../../core/i18n/i18n.service';

/**
 * « Mes grimoires »: the signed-in reader's spell books, as a table or as cards, with creation and
 * file import. Reached from the Library and from the Tavern.
 */
@Component({
  selector: 'dg-spellbook-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Parchment, Loading, Button, RouterLink, SpellBookForm, ListHeader],
  styles: `
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

    .table-scroll {
      overflow-x: auto;
    }

    .table {
      width: 100%;
      border-collapse: collapse;

      th,
      td {
        padding: var(--dg-space-2);
        text-align: left;
      }

      th {
        font-family: var(--dg-font-title);
        font-size: var(--dg-text-sm);
        font-weight: 600;
        letter-spacing: 0.05em;
        white-space: nowrap;
        color: var(--dg-blood);
        border-bottom: 2px solid var(--dg-parchment-border);
      }

      tbody tr {
        border-bottom: 1px solid var(--dg-parchment-shadow);
      }

      td a {
        font-weight: 600;
        color: var(--dg-blood);
      }
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
      <dg-list-header
        [heading]="t().library.spellbooks.title"
        storageKey="dg.spellbooks.view"
        [createLabel]="t().library.spellbooks.create"
        [(mode)]="view"
        (create)="toggleCreate()"
      >
        <dg-button variant="ghost" [loading]="importing()" (click)="file.click()">
          {{ t().library.spellbooks.import }}
        </dg-button>
        <input
          #file
          class="file"
          type="file"
          accept="application/json,.json"
          [attr.aria-label]="t().library.spellbooks.importFile"
          (change)="importFile($event)"
        />
      </dg-list-header>

      @if (importErrors(); as errors) {
        <div class="import-errors" role="alert">
          <p>{{ t().library.spellbooks.cannotImport }}</p>
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
        <section class="create" [attr.aria-label]="t().library.spellbooks.newBook">
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
          @if (view() === 'table') {
            <div class="table-scroll">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">{{ t().library.spellbooks.name }}</th>
                    <th scope="col">{{ t().library.spellbooks.ruleset }}</th>
                    <th scope="col">{{ t().library.spellbooks.spells }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (book of books(); track book.id) {
                    <tr>
                      <td>
                        <a [routerLink]="['/library/grimoires', book.id]">{{ book.name }}</a>
                      </td>
                      <td>{{ rulesetLabel(book) }}</td>
                      <td>{{ count(book) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
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
          }
        } @else {
          <p>{{ t().library.spellbooks.none }}</p>
        }
      } @else if (loaded().failed) {
        <div class="state">
          <dg-load-error
            [message]="t().library.spellbooks.loadError"
            [problem]="loadProblem()"
            (retry)="retry()"
          />
        </div>
      } @else {
        <div class="state">
          <dg-loading
            [label]="t().library.spellbooks.loading"
            [message]="t().library.spellbooks.loadingFlavour"
          />
        </div>
      }
    </dg-parchment>
  `,
})
export class SpellBookListPage {
  protected readonly t = inject(I18n).t;

  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly registry = inject(RulesetRegistry);
  private readonly selected = inject(SelectedRulesetService);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);
  /** `?ruleset=` (from a spell's page): opens the creation form on that ruleset. */
  private readonly requested = inject(ActivatedRoute).snapshot.queryParamMap.get('ruleset');

  private readonly form = viewChild(SpellBookForm);

  private readonly retries = new Subject<void>();
  protected readonly view = signal<ViewMode>('cards');
  protected readonly loaded = signal<LoadState<SpellBookDto[]>>(LOADING);
  protected readonly loadProblem = computed(() => toApiProblem(this.loaded().error));
  protected readonly books = signal<SpellBookDto[]>([]);
  protected readonly creating = signal(this.requested !== null);
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
    const current =
      choices.find((choice) => choice.id === this.requested)?.id ?? this.selected.current().id;
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
    return this.t().library.spellbooks.spellCount(book.spellList?.length ?? 0);
  }

  /** The API refuses a 101st book with a bare 409: this says why. */
  private limitReached(): ApiProblem {
    return { status: 409, detail: this.t().library.spellbooks.limitReached, errors: [] };
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
          this.notifier.success(this.t().library.spellbooks.created);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.notifier.error(this.limitReached());
          } else if (!(problem.status === 400 && this.form()?.showProblem(problem))) {
            this.notifier.error(problem, this.t().library.spellbooks.creating);
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
          this.notifier.success(this.t().library.spellbooks.imported);
        },
        error: (error: unknown) => {
          this.importing.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.notifier.error(this.limitReached());
          } else if (problem.status === 400) {
            this.importErrors.set(
              problem.errors.length > 0
                ? problem.errors
                : [
                    {
                      field: '',
                      message: problem.detail ?? this.t().library.spellbooks.invalidFile,
                    },
                  ],
            );
          } else {
            this.notifier.error(problem, this.t().library.spellbooks.importing);
          }
        },
      });
    // Lets the same file be chosen again after a fix.
    input.value = '';
  }
}
