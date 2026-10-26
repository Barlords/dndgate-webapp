import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { I18n } from '../../../core/i18n/i18n.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';

/**
 * Tells the reader that the reserve is full (the character cannot be kept outside an adventure)
 * and lets them cancel or choose to delete the character. Deleting is irreversible: the caller
 * still asks to retype the name (`ConfirmService.typeToConfirm`) before calling the API.
 */
@Injectable({ providedIn: 'root' })
export class ReserveFullDialog {
  private readonly confirmer = inject(ConfirmService);
  private readonly t = inject(I18n).t;

  /** Emits once: `delete` only when the reader chose it; Escape and the backdrop mean `cancel`. */
  ask(characterName: string): Observable<'cancel' | 'delete'> {
    const texts = this.t().tavern.characters;
    return this.confirmer
      .confirm({
        title: texts.reserveFullTitle,
        message: texts.reserveFullMessage(characterName),
        confirmLabel: texts.reserveFullDelete,
      })
      .pipe(map((confirmed) => (confirmed ? 'delete' : 'cancel')));
  }
}
