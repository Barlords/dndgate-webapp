import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { map } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  createAdventureNote,
  deleteAdventureNote,
  getAdventureNotesByAdventureId,
  patchAdventureNote,
} from '../../../core/api/generated/functions';
import { Button } from '../../../shared/ui/button/button';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { Paginator } from '../../../shared/ui/paginator/paginator';
import { Loading } from '../../../shared/ui/loading/loading';
import { NoteEditor, NoteValue } from './note-editor';
import { NoteTimeline } from './note-timeline';
import { NOTES_PAGE_SIZE, NotesPage } from './notes-page';

/**
 * « Notes du MJ »: the master writes them and sees them all; the players only read the public ones
 * (the API filters the list).
 */
@Component({
  selector: 'dg-gm-notes-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Button, Loading, Paginator, NoteEditor, NoteTimeline],
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
      <h1>Notes du MJ</h1>
      @if (canWrite() && !creating()) {
        <dg-button (click)="creating.set(true)">Ajouter une note</dg-button>
      }
    </div>
    @if (creating()) {
      <dg-note-editor
        [withVisibility]="true"
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
            [withVisibility]="true"
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
          <div class="state"><p>Aucune note du MJ pour le moment.</p></div>
        }
      }
      @case ('failed') {
        <div class="state">
          <dg-load-error
            message="Impossible de charger les notes du MJ."
            [problem]="loadProblem()"
            (retry)="load(0, false)"
          />
        </div>
      }
      @default {
        <div class="state">
          <dg-loading
            label="Chargement des notes du MJ"
            message="Le MJ fouille derrière son écran…"
          />
        </div>
      }
    }
  `,
})
export class GmNotesPage extends NotesPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;

  protected readonly canWrite = this.context.isMaster;
  protected readonly deleteTitle = 'Supprimer la note du MJ';

  protected fetch(adventureId: string, page: number) {
    return getAdventureNotesByAdventureId(this.http, this.rootUrl, {
      adventureId,
      page,
      size: NOTES_PAGE_SIZE,
    }).pipe(
      map(({ body }) => ({
        notes: (body.content ?? []).map((n) => ({
          id: n.id,
          content: n.content,
          date: n.date,
          visibility: n.noteVisibility,
        })),
        totalPages: body.totalPages ?? 1,
      })),
    );
  }

  protected createNote(adventureId: string, { date, content, visibility }: NoteValue) {
    return createAdventureNote(this.http, this.rootUrl, {
      body: { adventureId, date, content, visibility },
    });
  }

  protected patchNote(id: string, { date, content, visibility }: NoteValue) {
    return patchAdventureNote(this.http, this.rootUrl, {
      id,
      body: { date, content, visibility },
    });
  }

  protected deleteNote(id: string) {
    return deleteAdventureNote(this.http, this.rootUrl, { id, body: { confirm: true } });
  }
}
