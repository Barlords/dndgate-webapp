import { CdkMenu, CdkMenuItem, CdkMenuItemCheckbox, CdkMenuTrigger } from '@angular/cdk/menu';
import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { map, Observable } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  addSpellToBook,
  getMySpellBooks,
  removeSpellFromBook,
} from '../../../core/api/generated/functions';
import { SpellBookDto } from '../../../core/api/generated/models/spell-book-dto';
import { AuthService } from '../../../core/auth/auth.service';
import { toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';

/**
 * « Ajouter au grimoire » menu of a spell's page: the reader's spell books of the spell's ruleset,
 * ticked when they already hold the spell. Choosing one adds or removes the spell.
 */
@Component({
  selector: 'dg-add-to-spellbook',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkMenuTrigger, CdkMenu, CdkMenuItem, CdkMenuItemCheckbox, RouterLink],
  styles: `
    :host {
      display: block;
      margin: var(--dg-space-3) 0;
      text-align: center;
    }

    .add-to-book__trigger {
      padding: var(--dg-space-2) var(--dg-space-4);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--dg-blood);
      background: var(--dg-vellum);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      cursor: pointer;
    }

    .add-to-book__menu {
      min-width: 14rem;
      margin: var(--dg-space-1) 0 0;
      padding: var(--dg-space-1);
      list-style: none;
      font-family: var(--dg-font-body);
      color: var(--dg-ink);
      text-align: left;
      background: linear-gradient(var(--dg-parchment-0), var(--dg-parchment-1));
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      box-shadow:
        inset 0 0 12px var(--dg-parchment-shadow),
        0 6px 18px #17100a99;
    }

    .add-to-book__item {
      display: flex;
      align-items: center;
      gap: var(--dg-space-2);
      padding: var(--dg-space-2) var(--dg-space-3);
      color: var(--dg-ink);
      text-decoration: none;
      border-radius: var(--dg-radius-sm);
      cursor: pointer;
      transition: background var(--dg-transition);
    }

    .add-to-book__item:hover,
    .add-to-book__item.cdk-menu-item-active,
    .add-to-book__item:focus-visible {
      background: var(--dg-gold-light);
    }

    .add-to-book__item[aria-disabled='true'] {
      opacity: 0.55;
      cursor: progress;
    }

    .add-to-book__mark {
      flex: 0 0 1.1rem;
      text-align: center;
      font-weight: 700;
      color: var(--dg-blood);
    }

    .add-to-book__create {
      margin-top: var(--dg-space-1);
      padding-top: var(--dg-space-2);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.08em;
      color: var(--dg-blood);
      border-top: 1px solid var(--dg-parchment-border);
      border-radius: 0 0 var(--dg-radius-sm) var(--dg-radius-sm);
    }

    .add-to-book__empty {
      padding: var(--dg-space-2) var(--dg-space-3);
      font-style: italic;
      color: var(--dg-ink-muted);
    }

    .add-to-book__trigger:focus-visible,
    .add-to-book__item:focus-visible {
      outline: 2px solid var(--dg-focus-color);
      outline-offset: 2px;
    }
  `,
  template: `
    @if (auth.isAuthenticated()) {
      <button
        type="button"
        class="add-to-book__trigger"
        [cdkMenuTriggerFor]="menu"
        aria-haspopup="menu"
      >
        Ajouter au grimoire
      </button>
      <ng-template #menu>
        <ul cdkMenu class="add-to-book__menu" aria-label="Mes grimoires">
          @for (book of fitting(); track book.id) {
            <li
              cdkMenuItemCheckbox
              class="add-to-book__item"
              [cdkMenuItemChecked]="holds(book)"
              [attr.aria-disabled]="isPending(book) || null"
              (cdkMenuItemTriggered)="toggle(book)"
            >
              <span class="add-to-book__mark" aria-hidden="true">{{ holds(book) ? '✓' : '' }}</span>
              {{ book.name }}
            </li>
          } @empty {
            <li class="add-to-book__empty" role="none">Aucun grimoire pour ce jeu de règles.</li>
          }
          <li role="none">
            <a
              cdkMenuItem
              class="add-to-book__item add-to-book__create"
              [routerLink]="['/library/grimoires']"
              [queryParams]="{ ruleset: rulesetId() }"
            >
              Créer un grimoire
            </a>
          </li>
        </ul>
      </ng-template>
    }
  `,
})
export class AddToSpellbook {
  protected readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  readonly rulesetId = input.required<string>();
  readonly spellIndex = input.required<string>();

  private readonly books = signal<SpellBookDto[]>([]);
  private readonly pending = signal<ReadonlySet<string>>(new Set());
  protected readonly fitting = computed(() =>
    this.books().filter((book) => book.ruleset === this.rulesetId()),
  );

  constructor() {
    if (this.auth.isAuthenticated()) {
      getMySpellBooks(this.http, this.rootUrl)
        .pipe(
          map((response) => response.body),
          takeUntilDestroyed(),
        )
        .subscribe({
          next: (books) => this.books.set(books),
          error: (error: unknown) =>
            this.notifier.error(toApiProblem(error), 'Chargement des grimoires'),
        });
    }
  }

  protected isPending(book: SpellBookDto): boolean {
    return this.pending().has(book.id ?? '');
  }

  protected holds(book: SpellBookDto): boolean {
    return book.spellList?.includes(this.spellIndex()) ?? false;
  }

  protected toggle(book: SpellBookDto): void {
    const id = book.id ?? '';
    if (this.pending().has(id)) {
      return; // A request for this book is already on its way.
    }
    const adding = !this.holds(book);
    const spellIndex = this.spellIndex();
    // The removal answers 204: the book is updated here.
    const call: Observable<SpellBookDto> = adding
      ? addSpellToBook(this.http, this.rootUrl, { id, body: { spellIndex } }).pipe(
          map((response) => response.body),
        )
      : removeSpellFromBook(this.http, this.rootUrl, { id, spellIndex }).pipe(
          map(() => ({
            ...book,
            spellList: (book.spellList ?? []).filter((index) => index !== spellIndex),
          })),
        );
    this.pending.update((ids) => new Set(ids).add(id));
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.settle(id);
        this.books.update((books) => books.map((b) => (b.id === id ? updated : b)));
        this.notifier.success(adding ? `Ajouté à ${book.name}` : `Retiré de ${book.name}`);
      },
      error: (error: unknown) => {
        this.settle(id);
        this.notifier.error(
          toApiProblem(error),
          adding ? 'Ajout au grimoire' : 'Retrait du grimoire',
        );
      },
    });
  }

  private settle(id: string): void {
    this.pending.update((ids) => {
      const next = new Set(ids);
      next.delete(id);
      return next;
    });
  }
}
