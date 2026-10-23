import { Injectable, signal } from '@angular/core';
import { ApiProblem } from './api-problem';

export interface ToastAction {
  label: string;
  run: () => void;
}

export interface Toast {
  id: number;
  kind: 'success' | 'error';
  message: string;
  detail?: string;
  /** Correlation id of the failed call, to quote to support. */
  reference?: string;
  action?: ToastAction;
}

const AUTO_CLOSE_MS = 6000;

function messageFor(problem: ApiProblem): { message: string; detail?: string } {
  const { status, detail } = problem;
  if (status === 0) {
    return { message: 'Le serveur ne répond pas.' };
  }
  if (status === 403) {
    return { message: 'Tu n’as pas les droits pour cette action.', detail };
  }
  if (status === 404) {
    return { message: 'Introuvable.', detail };
  }
  if (status === 409) {
    return { message: detail ? `Conflit : ${detail}` : 'Conflit.' };
  }
  if (status === 429) {
    const wait = problem.retryAfterSeconds;
    return {
      message:
        wait === undefined
          ? 'Trop de tentatives, réessaie plus tard.'
          : `Trop de tentatives, réessaie dans ${wait} s.`,
    };
  }
  if (status >= 500) {
    return { message: 'Erreur du serveur.', detail };
  }
  return { message: 'Une erreur est survenue.', detail };
}

/** Stack of toasts shown by the toast outlet. */
@Injectable({ providedIn: 'root' })
export class NotifierService {
  private nextId = 0;
  private readonly list = signal<Toast[]>([]);
  readonly toasts = this.list.asReadonly();

  success(message: string): void {
    this.push({ kind: 'success', message }, true);
  }

  /** `context` says what was being done, e.g. « Chargement de l’aventure ». */
  error(problem: ApiProblem, context?: string): void {
    const { message, detail } = messageFor(problem);
    this.push(
      {
        kind: 'error',
        message: context ? `${context} : ${message}` : message,
        detail,
        reference: problem.correlationId,
      },
      true,
    );
  }

  /** Stays until dismissed or acted on: the reader has to choose. */
  conflict(onReload: () => void): void {
    this.push(
      {
        kind: 'error',
        message: 'Quelqu’un a modifié cet élément entre-temps.',
        action: { label: 'Recharger', run: onReload },
      },
      false,
    );
  }

  dismiss(id: number): void {
    this.list.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }

  /** Runs the toast's action, then closes it. */
  act(toast: Toast): void {
    toast.action?.run();
    this.dismiss(toast.id);
  }

  private push(toast: Omit<Toast, 'id'>, autoClose: boolean): void {
    const id = this.nextId++;
    this.list.update((toasts) => [...toasts, { ...toast, id }]);
    if (autoClose) {
      setTimeout(() => this.dismiss(id), AUTO_CLOSE_MS);
    }
  }
}
