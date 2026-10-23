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
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();
  /** Toasts that only the reader may close. */
  private readonly sticky = new Set<number>();
  readonly toasts = this.list.asReadonly();

  success(message: string): void {
    this.push({ kind: 'success', message }, true);
  }

  /** `context` says what was being done, e.g. « Chargement de l’aventure ». */
  error(problem: ApiProblem, context?: string): void {
    if (problem.status === 401) {
      return; // The interceptor already ended the session and redirected to the login page.
    }
    const { message, detail } = messageFor(problem);
    this.push(
      {
        kind: 'error',
        message: context ? `${context} : ${message}` : message,
        detail,
        reference: problem.correlationId,
      },
      // The reference has to stay long enough to be selected and copied.
      problem.correlationId === undefined,
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

  /** Hover or focus on a toast: it must not vanish under the reader. */
  pause(id: number): void {
    this.clearTimer(id);
  }

  /** Hover and focus are gone: the 6 s start over. */
  resume(id: number): void {
    if (!this.sticky.has(id) && this.list().some((toast) => toast.id === id)) {
      this.arm(id);
    }
  }

  dismiss(id: number): void {
    this.clearTimer(id);
    this.sticky.delete(id);
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
      this.arm(id);
    } else {
      this.sticky.add(id);
    }
  }

  private arm(id: number): void {
    this.clearTimer(id);
    this.timers.set(
      id,
      setTimeout(() => this.dismiss(id), AUTO_CLOSE_MS),
    );
  }

  private clearTimer(id: number): void {
    clearTimeout(this.timers.get(id));
    this.timers.delete(id);
  }
}
