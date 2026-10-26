import { Dialog, DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  Injectable,
  signal,
} from '@angular/core';
import { Observable } from 'rxjs';
import { I18n } from '../../../core/i18n/i18n.service';
import { Button } from '../../../shared/ui/button/button';
import { Parchment } from '../../../shared/ui/parchment/parchment';

/** A character that would go back to the reserve when its owner leaves the adventure. */
export interface QuitCharacter {
  id: string;
  name: string;
}

export interface QuitReserveData {
  titleId: string;
  messageId: string;
  characters: QuitCharacter[];
  /** How many places the reserve lacks if every character goes back to it. */
  missing: number;
}

let nextId = 0;

/**
 * Leaving an adventure with a full reserve: the reader ticks the characters to delete permanently
 * until the others fit. Confirming is impossible until enough are ticked.
 */
@Component({
  selector: 'dg-quit-reserve-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Parchment],
  template: `
    <dg-parchment>
      <form (submit)="submit($event)">
        <h2 class="title" [id]="data.titleId">{{ t().tavern.overview.quitReserveTitle }}</h2>
        <p class="message" [id]="data.messageId">{{ t().tavern.overview.quitReserveMessage }}</p>
        <fieldset>
          <legend>{{ t().tavern.overview.quitReserveChoose }}</legend>
          @for (character of data.characters; track character.id) {
            <label class="choice">
              <input
                type="checkbox"
                [checked]="checked().has(character.id)"
                (change)="toggle(character.id)"
              />
              {{ t().tavern.overview.quitReserveDelete(character.name) }}
            </label>
          }
        </fieldset>
        <p class="status" role="status" aria-live="polite">
          {{
            stillMissing() > 0
              ? t().tavern.overview.quitReserveMissing(stillMissing())
              : t().tavern.overview.quitReserveEnough
          }}
        </p>
        <div class="actions">
          <dg-button variant="ghost" (click)="cancel()">{{ t().common.actions.cancel }}</dg-button>
          <dg-button type="submit" variant="danger" [disabled]="stillMissing() > 0">
            {{ t().tavern.overview.quitReserveConfirm }}
          </dg-button>
        </div>
      </form>
    </dg-parchment>
  `,
  styles: `
    :host {
      display: block;
    }

    .title {
      font-size: var(--dg-text-xl);
    }

    fieldset {
      margin: 0 0 var(--dg-space-4);
      padding: 0;
      border: 0;
    }

    legend {
      margin-bottom: var(--dg-space-2);
      font-weight: 600;
    }

    .choice {
      display: flex;
      align-items: center;
      gap: var(--dg-space-2);
      padding: var(--dg-space-1) 0;
    }

    .status {
      font-style: italic;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--dg-space-3);
      margin-top: var(--dg-space-5);
    }
  `,
})
export class QuitReserveDialog {
  protected readonly t = inject(I18n).t;
  protected readonly data = inject<QuitReserveData>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<string[]>>(DialogRef);

  protected readonly checked = signal<ReadonlySet<string>>(new Set());
  protected readonly stillMissing = computed(() =>
    Math.max(0, this.data.missing - this.checked().size),
  );

  protected toggle(id: string): void {
    this.checked.update((current) => {
      const next = new Set(current);
      if (!next.delete(id)) {
        next.add(id);
      }
      return next;
    });
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (this.stillMissing() === 0) {
      this.ref.close([...this.checked()]);
    }
  }

  protected cancel(): void {
    this.ref.close();
  }
}

/** Opens the dialog on subscription; emits the ids to delete, or `null` when cancelled. */
@Injectable({ providedIn: 'root' })
export class QuitReserveDialogService {
  private readonly dialog = inject(Dialog);

  ask(characters: QuitCharacter[], missing: number): Observable<string[] | null> {
    return new Observable((subscriber) => {
      const id = nextId++;
      const data: QuitReserveData = {
        titleId: `dg-quit-title-${id}`,
        messageId: `dg-quit-message-${id}`,
        characters,
        missing,
      };
      const ref = this.dialog.open<string[], QuitReserveData>(QuitReserveDialog, {
        data,
        role: 'dialog',
        ariaLabelledBy: data.titleId,
        ariaDescribedBy: data.messageId,
        autoFocus: 'first-tabbable',
        panelClass: 'dg-dialog-panel',
        backdropClass: 'dg-dialog-backdrop',
      });
      let closed = false;
      const subscription = ref.closed.subscribe((result) => {
        closed = true;
        subscriber.next(result ?? null);
        subscriber.complete();
      });
      return () => {
        subscription.unsubscribe();
        if (!closed) {
          ref.close();
        }
      };
    });
  }
}
