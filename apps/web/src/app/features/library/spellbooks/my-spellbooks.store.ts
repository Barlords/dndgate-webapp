import { HttpClient } from '@angular/common/http';
import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { map, Observable } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  addSpellToBook,
  getMySpellBooks,
  removeSpellFromBook,
} from '../../../core/api/generated/functions';
import { SpellBookDto } from '../../../core/api/generated/models/spell-book-dto';
import { toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';

/**
 * The reader's spell books, shared by every « add to a spell book » button of a page: they are read
 * once however many buttons the page shows (one per row of the spell list). Provided by the page
 * itself, so that each visit reads them afresh.
 */
@Injectable()
export class MySpellBooksStore {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly booksState = signal<SpellBookDto[]>([]);
  /** `<book id>/<spell index>` of the changes on their way. */
  private readonly pending = signal<ReadonlySet<string>>(new Set());
  private requested = false;

  readonly books = this.booksState.asReadonly();

  /** Reads the books the first time it is called; later calls do nothing. */
  load(): void {
    if (this.requested) {
      return;
    }
    this.requested = true;
    getMySpellBooks(this.http, this.rootUrl)
      .pipe(
        map((response) => response.body),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (books) => this.booksState.set(books),
        error: (error: unknown) => {
          // Asked again by the next button that needs them.
          this.requested = false;
          this.notifier.error(toApiProblem(error), 'Chargement des grimoires');
        },
      });
  }

  holds(book: SpellBookDto, spellIndex: string): boolean {
    return book.spellList?.includes(spellIndex) ?? false;
  }

  isPending(book: SpellBookDto, spellIndex: string): boolean {
    return this.pending().has(`${book.id}/${spellIndex}`);
  }

  /** Adds the spell to the book, or removes it when the book holds it already. */
  toggle(book: SpellBookDto, spellIndex: string): void {
    const id = book.id ?? '';
    const key = `${id}/${spellIndex}`;
    if (this.pending().has(key)) {
      return; // This very change is already on its way.
    }
    const adding = !this.holds(book, spellIndex);
    const call: Observable<unknown> = adding
      ? addSpellToBook(this.http, this.rootUrl, { id, body: { spellIndex } })
      : removeSpellFromBook(this.http, this.rootUrl, { id, spellIndex });
    this.pending.update((keys) => new Set(keys).add(key));
    // A change answered once the page is gone is not reported.
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.settle(key);
        // Applied to the book as it is now: other spells may have changed in it meanwhile.
        this.booksState.update((books) =>
          books.map((current) =>
            current.id === id ? withSpell(current, spellIndex, adding) : current,
          ),
        );
        this.notifier.success(adding ? `Ajouté à ${book.name}` : `Retiré de ${book.name}`);
      },
      error: (error: unknown) => {
        this.settle(key);
        this.notifier.error(
          toApiProblem(error),
          adding ? 'Ajout au grimoire' : 'Retrait du grimoire',
        );
      },
    });
  }

  private settle(key: string): void {
    this.pending.update((keys) => {
      const next = new Set(keys);
      next.delete(key);
      return next;
    });
  }
}

function withSpell(book: SpellBookDto, spellIndex: string, present: boolean): SpellBookDto {
  const others = (book.spellList ?? []).filter((index) => index !== spellIndex);
  return { ...book, spellList: present ? [...others, spellIndex] : others };
}
