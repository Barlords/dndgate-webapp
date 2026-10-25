import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { Button } from '../../../shared/ui/button/button';
import { NoteEditor, NoteValue, NoteVisibility } from './note-editor';
import { I18n } from '../../../core/i18n/i18n.service';

/** A note as the timeline shows it, whichever kind of note it is. */
export interface NoteView {
  id: string;
  content: string;
  /** `YYYY-MM-DD`. */
  date: string;
  /** Set where notes have a visibility. */
  visibility?: NoteVisibility;
}

const FORMAT: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'long', year: 'numeric' };

/** « 2 mai 2026 » in `fr-FR`, from `YYYY-MM-DD` without going through a time zone. */
export function formatNoteDate(date: string, locale = 'fr-FR'): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString(locale, FORMAT);
}

/**
 * Notes as a vertical timeline on parchment, each under its date as a gilded marker. The text keeps
 * its line breaks and is never read as markup. The note being edited is swapped for the editor.
 */
@Component({
  selector: 'dg-note-timeline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, NoteEditor],
  styles: `
    .timeline {
      position: relative;
      margin: 0;
      padding: 0 0 0 var(--dg-space-5);
      list-style: none;
    }

    // The gilded thread the markers hang on.
    .timeline::before {
      content: '';
      position: absolute;
      top: 0.5rem;
      bottom: 0.5rem;
      left: 0.45rem;
      width: 2px;
      background: linear-gradient(var(--dg-gold), var(--dg-gold-deep));
    }

    .entry {
      position: relative;
      margin-bottom: var(--dg-space-4);
    }

    .marker {
      position: absolute;
      top: 1rem;
      left: calc(var(--dg-space-5) * -1 + 0.1rem);
      width: 0.8rem;
      height: 0.8rem;
      background: var(--dg-gold);
      border: 2px solid var(--dg-gold-deep);
      border-radius: 50%;
      box-shadow: 0 0 0 3px #17100a66;
    }

    .note {
      padding: var(--dg-space-4);
      color: var(--dg-ink);
      background: var(--dg-vellum);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      box-shadow:
        inset 0 0 0 3px var(--dg-vellum),
        inset 0 0 0 4px #c9a24a66;
    }

    .meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--dg-space-3);
      margin-bottom: var(--dg-space-2);
    }

    time {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--dg-gold-ink);
    }

    .badge {
      padding: 0.1rem 0.6rem;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      color: var(--dg-gold-light);
      background: var(--dg-blood);
      border-radius: 999px;
    }

    .content {
      margin: 0;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-2);
      margin-top: var(--dg-space-3);
    }
  `,
  template: `
    <ol class="timeline">
      @for (note of notes(); track note.id) {
        <li class="entry">
          <span class="marker" aria-hidden="true"></span>
          @if (note.id === editingId()) {
            <dg-note-editor
              [initial]="editedValue()"
              [withVisibility]="withVisibility()"
              [busy]="busy()"
              [submitLabel]="t().common.actions.save"
              (saved)="save.emit({ id: note.id, value: $event })"
              (cancelled)="cancelEdit.emit()"
            />
          } @else {
            <article class="note">
              <div class="meta">
                <time [attr.datetime]="note.date">{{ formatDate(note.date) }}</time>
                @if (note.visibility === 'PRIVATE') {
                  <span class="badge">{{ t().tavern.notes.private }}</span>
                }
              </div>
              <p class="content">{{ note.content }}</p>
              @if (canEdit()) {
                <div class="actions">
                  <dg-button variant="ghost" (click)="edit.emit(note.id)">{{
                    t().common.actions.edit
                  }}</dg-button>
                  <dg-button variant="danger" (click)="remove.emit(note.id)">{{
                    t().common.actions.delete
                  }}</dg-button>
                </div>
              }
            </article>
          }
        </li>
      }
    </ol>
  `,
})
export class NoteTimeline {
  protected readonly t = inject(I18n).t;

  readonly notes = input.required<NoteView[]>();
  readonly canEdit = input(false);
  readonly withVisibility = input(false);
  readonly editingId = input<string | null>(null);
  readonly busy = input(false);
  readonly edit = output<string>();
  readonly cancelEdit = output<void>();
  readonly save = output<{ id: string; value: NoteValue }>();
  readonly remove = output<string>();

  /** Kept stable: the editor starts over each time it is handed a new value. */
  protected readonly editedValue = computed<NoteValue | null>(() => {
    const note = this.notes().find((n) => n.id === this.editingId());
    return note
      ? { date: note.date, content: note.content, visibility: note.visibility ?? 'PUBLIC' }
      : null;
  });

  protected formatDate(date: string): string {
    return formatNoteDate(date, this.t().common.locale);
  }
}
