import { Dialog } from '@angular/cdk/dialog';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ConfirmDialog, ConfirmDialogData, ConfirmDialogResult } from './confirm-dialog';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel: string;
}

export interface TypeToConfirmOptions extends ConfirmOptions {
  /** When given, confirming stays impossible until the reader types it (spaces around ignored). */
  expected?: string;
  inputLabel: string;
  inputType: 'text' | 'password';
}

let nextId = 0;

/**
 * Asks the reader before an action. The dialog opens on subscription and closes if the subscriber
 * leaves first; Escape, the backdrop and "Annuler" all count as a refusal.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(Dialog);

  confirm(opts: ConfirmOptions): Observable<boolean> {
    return this.open({ ...this.ids(), ...opts }).pipe(map((result) => result === true));
  }

  /** Emits the typed text exactly as typed (a password keeps its spaces), or null if cancelled. */
  typeToConfirm(opts: TypeToConfirmOptions): Observable<string | null> {
    const { inputLabel, inputType, expected, ...texts } = opts;
    return this.open({
      ...this.ids(),
      ...texts,
      input: { label: inputLabel, type: inputType, expected },
    }).pipe(map((result) => (typeof result === 'string' ? result : null)));
  }

  private ids(): Pick<ConfirmDialogData, 'titleId' | 'messageId'> {
    const id = nextId++;
    return { titleId: `dg-confirm-title-${id}`, messageId: `dg-confirm-message-${id}` };
  }

  private open(data: ConfirmDialogData): Observable<ConfirmDialogResult | undefined> {
    return new Observable((subscriber) => {
      const ref = this.dialog.open<ConfirmDialogResult, ConfirmDialogData>(ConfirmDialog, {
        data,
        role: 'alertdialog',
        ariaLabelledBy: data.titleId,
        ariaDescribedBy: data.messageId,
        autoFocus: 'first-tabbable',
        panelClass: 'dg-dialog-panel',
        backdropClass: 'dg-dialog-backdrop',
      });
      let closed = false;
      const subscription = ref.closed.subscribe((result) => {
        closed = true;
        subscriber.next(result);
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
