import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Dictionary } from '../i18n/dictionary';
import { I18n } from '../i18n/i18n.service';
import { featureName, limitName, planName } from '../plans/plan-texts';
import { ApiProblem, PlanRefusal } from './api-problem';

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

export interface ErrorOptions {
  /**
   * The reader is the master of the adventure: a refusal decided by the master's plan is then about
   * their own plan, and offers it rather than telling them to ask their master.
   */
  readerIsMaster?: boolean;
}

function messageFor(
  problem: ApiProblem,
  t: Dictionary['common']['errors'],
): { message: string; detail?: string } {
  const { status, detail } = problem;
  if (status === 0) {
    return { message: t.noServer };
  }
  if (status === 403) {
    return { message: t.forbidden, detail };
  }
  if (status === 404) {
    return { message: t.notFound, detail };
  }
  if (status === 409) {
    return { message: detail ? t.conflictWith(detail) : t.conflict };
  }
  if (status === 429) {
    const wait = problem.retryAfterSeconds;
    return { message: wait === undefined ? t.tooMany : t.tooManyFor(wait) };
  }
  if (status >= 500) {
    return { message: t.server, detail };
  }
  return { message: t.unknown, detail };
}

/** Stack of toasts shown by the toast outlet, written in the language of the moment they appear. */
@Injectable({ providedIn: 'root' })
export class NotifierService {
  private readonly t = inject(I18n).t;
  private readonly router = inject(Router);

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
  error(problem: ApiProblem, context?: string, options: ErrorOptions = {}): void {
    if (problem.status === 401) {
      return; // The interceptor already ended the session and redirected to the login page.
    }
    if (problem.plan) {
      this.refusal(problem.plan, context, options.readerIsMaster === true);
      return;
    }
    const t = this.t().common;
    const { message, detail } = messageFor(problem, t.errors);
    this.push(
      {
        kind: 'error',
        message: context ? t.errors.inContext(context, message) : message,
        detail,
        reference: problem.correlationId,
      },
      // The reference has to stay long enough to be selected and copied.
      problem.correlationId === undefined,
    );
  }

  /** An error whose text the caller already knows (not derived from an HTTP status). */
  failure(message: string): void {
    this.push({ kind: 'error', message }, true);
  }

  /** A plan refusal stays until dismissed: the reader has to see which plan would allow it. */
  private refusal(plan: PlanRefusal, context: string | undefined, readerIsMaster: boolean): void {
    const t = this.t();
    const what =
      plan.kind === 'limit'
        ? t.plans.limitReached(limitName(plan.key, t), plan.max)
        : t.plans.featureMissing(featureName(plan.key, t));
    let message = what;
    let action: ToastAction | undefined;
    if (plan.upgradePlanId !== null) {
      const name = planName(plan.upgradePlanId, t);
      if (plan.decidedBy === 'master' && !readerIsMaster) {
        message = `${what} ${t.plans.askMaster(name)}`;
      } else {
        message = `${what} ${t.plans.upgradeTo(name)}`;
        action = { label: t.plans.seeOffers, run: () => void this.router.navigateByUrl('/plans') };
      }
    }
    this.push(
      {
        kind: 'error',
        message: context ? t.common.errors.inContext(context, message) : message,
        action,
      },
      false,
    );
  }

  /** Stays until dismissed or acted on: the reader has to choose. */
  conflict(onReload: () => void): void {
    this.push(
      {
        kind: 'error',
        message: this.t().common.errors.concurrentChange,
        action: { label: this.t().common.actions.reload, run: onReload },
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
