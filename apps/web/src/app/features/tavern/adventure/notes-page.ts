import {
  Directive,
  DestroyRef,
  effect,
  inject,
  Signal,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';
import { toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AdventureContext } from './adventure-context.service';
import { NoteEditor, NoteValue } from './note-editor';
import { NoteView } from './note-timeline';

export const NOTES_PAGE_SIZE = 20;

export interface NotesPageResult {
  notes: NoteView[];
  totalPages: number;
}

type LoadState = 'loading' | 'ready' | 'failed';

/**
 * What the chronicle and the game master's notes have in common: a paged list, newest first, a
 * creation form, in-place edition and deletion after confirmation. A kind of note says how to talk
 * to the API and who may write.
 */
@Directive()
export abstract class NotesPage {
  protected readonly context = inject(AdventureContext);
  private readonly confirmer = inject(ConfirmService);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<LoadState>('loading');
  protected readonly notes = signal<NoteView[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(1);
  protected readonly creating = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly creator = viewChild(NoteEditor);
  private requested = false;

  /** Whether the reader may add, edit and delete notes. */
  protected abstract readonly canWrite: Signal<boolean>;
  protected abstract fetch(adventureId: string, page: number): Observable<NotesPageResult>;
  protected abstract createNote(adventureId: string, value: NoteValue): Observable<unknown>;
  protected abstract patchNote(id: string, value: NoteValue): Observable<unknown>;
  protected abstract deleteNote(id: string): Observable<unknown>;
  protected abstract readonly deleteTitle: string;

  constructor() {
    effect(() => {
      // No request before the reader is known to be a participant: a visitor is redirected away
      // by the shell, and the API would refuse them.
      if (this.context.state() === 'ready' && this.context.isParticipant()) {
        untracked(() => {
          if (!this.requested) {
            this.load(0, false);
          }
        });
      }
    });
  }

  protected adventureId(): string {
    return this.context.adventure()!.id!;
  }

  /** `quiet` keeps the list on screen meanwhile and reports a failure as a toast. */
  protected load(page: number, quiet: boolean): void {
    this.requested = true;
    if (!quiet) {
      this.state.set('loading');
    }
    this.fetch(this.adventureId(), page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ notes, totalPages }) => {
          if (notes.length === 0 && page > 0) {
            // The last note of the last page is gone: show the page before.
            this.load(page - 1, true);
            return;
          }
          this.notes.set(notes);
          this.page.set(page);
          this.totalPages.set(totalPages);
          this.state.set('ready');
        },
        error: (error: unknown) => {
          if (quiet) {
            this.notifier.error(toApiProblem(error), 'Chargement des notes');
          } else {
            this.state.set('failed');
          }
        },
      });
  }

  protected reload(): void {
    this.editingId.set(null);
    this.load(this.page(), true);
  }

  protected create(value: NoteValue): void {
    this.saving.set(true);
    this.createNote(this.adventureId(), value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.creating.set(false);
          this.load(0, true);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.creator()?.reject('Cette aventure a atteint la limite de 500 notes de ce type.');
          } else {
            this.notifier.error(problem, 'Ajout de la note');
          }
        },
      });
  }

  protected save(id: string, value: NoteValue): void {
    this.saving.set(true);
    this.patchNote(id, value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.editingId.set(null);
          this.load(this.page(), true);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.notifier.conflict(() => this.reload());
          } else {
            this.notifier.error(problem, 'Modification de la note');
          }
        },
      });
  }

  protected remove(id: string): void {
    this.confirmer
      .confirm({
        title: this.deleteTitle,
        message: 'Cette suppression est irréversible.',
        confirmLabel: 'Supprimer',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.deleteNote(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.notifier.success('Note supprimée.');
              this.load(this.page(), true);
            },
            error: (error: unknown) =>
              this.notifier.error(toApiProblem(error), 'Suppression de la note'),
          });
      });
  }
}
