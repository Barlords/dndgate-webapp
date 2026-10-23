import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { map } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  createHistoricalNote,
  deleteHistoricalNote,
  getHistoricalNotesByAdventureId,
  patchHistoricalNote,
} from '../../../core/api/generated/functions';
import { Button } from '../../../shared/ui/button/button';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { Paginator } from '../../../shared/ui/paginator/paginator';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { NoteEditor, NoteValue } from './note-editor';
import { NoteTimeline } from './note-timeline';
import { NOTES_PAGE_SIZE, NotesPage } from './notes-page';

/** « Chronique »: the story of the table so far, written by any participant. */
@Component({
  selector: 'dg-chronicle-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Button, Spinner, Paginator, NoteEditor, NoteTimeline],
  styleUrl: '../tavern-page.scss',
  styles: `
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: var(--dg-space-3);
      margin-bottom: var(--dg-space-4);

      h1 {
        margin: 0;
      }
    }
  `,
  template: `
    <div class="head">
      <h1>Chronique</h1>
      @if (canWrite() && !creating()) {
        <dg-button (click)="creating.set(true)">Ajouter une note</dg-button>
      }
    </div>
    @if (creating()) {
      <dg-note-editor
        [busy]="saving()"
        (saved)="create($event)"
        (cancelled)="creating.set(false)"
      />
    }
    @switch (state()) {
      @case ('ready') {
        @if (notes().length) {
          <dg-note-timeline
            [notes]="notes()"
            [canEdit]="canWrite()"
            [editingId]="editingId()"
            [busy]="saving()"
            (edit)="editingId.set($event)"
            (cancelEdit)="editingId.set(null)"
            (save)="save($event.id, $event.value)"
            (remove)="remove($event)"
          />
          <dg-paginator
            [page]="page()"
            [totalPages]="totalPages()"
            (pageChange)="load($event, true)"
          />
        } @else {
          <div class="state"><p>La chronique est encore vierge.</p></div>
        }
      }
      @case ('failed') {
        <div class="state">
          <dg-load-error
            message="Impossible de charger la chronique."
            [problem]="loadProblem()"
            (retry)="load(0, false)"
          />
        </div>
      }
      @default {
        <div class="state"><dg-spinner label="Chargement de la chronique" /></div>
      }
    }
  `,
})
export class ChroniclePage extends NotesPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;

  protected readonly canWrite = this.context.isParticipant;
  protected readonly deleteTitle = 'Supprimer la note de la chronique';

  protected fetch(adventureId: string, page: number) {
    return getHistoricalNotesByAdventureId(this.http, this.rootUrl, {
      adventureId,
      page,
      size: NOTES_PAGE_SIZE,
    }).pipe(map(({ body }) => ({ notes: body.content ?? [], totalPages: body.totalPages ?? 1 })));
  }

  protected createNote(adventureId: string, { date, content }: NoteValue) {
    return createHistoricalNote(this.http, this.rootUrl, {
      body: { adventureId, date, content },
    });
  }

  protected patchNote(id: string, { date, content }: NoteValue) {
    return patchHistoricalNote(this.http, this.rootUrl, { id, body: { date, content } });
  }

  protected deleteNote(id: string) {
    return deleteHistoricalNote(this.http, this.rootUrl, { id, body: { confirm: true } });
  }
}
