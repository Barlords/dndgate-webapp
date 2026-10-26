import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal, Signal, WritableSignal } from '@angular/core';
import { map, Observable, shareReplay } from 'rxjs';
import { ApiConfiguration } from '../api/generated/api-configuration';
import { getAccountById } from '../api/generated/functions';
import { PublicAccountDto } from '../api/generated/models/public-account-dto';
import { Dictionary } from '../i18n/dictionary';
import { I18n } from '../i18n/i18n.service';

/** A failed lookup is not retried sooner than this: templates read `pseudo()` on every render. */
export const RETRY_COOLDOWN_MS = 30_000;

/** Display name of an account: its pseudo, or « Compte supprimé » once anonymized. */
export function displayName(account: PublicAccountDto, t: Dictionary): string {
  return account.anonymized ? t.account.names.deleted : (account.pseudo ?? t.account.names.unknown);
}

/** What is known of an account: written in the reader's language only when shown. */
type Known =
  { kind: 'loading' } | { kind: 'found'; account: PublicAccountDto } | { kind: 'failed' };

/** A mark shown next to a pseudo: the founder mark first, then the plan badge. */
export type AccountBadgeKind = 'founder' | 'dungeon-master' | 'legend';

interface PseudoEntry {
  known: WritableSignal<Known>;
  name: Signal<string>;
  badges: Signal<AccountBadgeKind[]>;
  /** When the last lookup failed, or null while loading or loaded. */
  failedAt: number | null;
}

function badgesOf(known: Known): AccountBadgeKind[] {
  if (known.kind !== 'found') {
    return [];
  }
  const { founder, planBadge } = known.account;
  return [...(founder ? (['founder'] as const) : []), ...(planBadge ? [planBadge] : [])];
}

/**
 * Who an account id is. Accounts are fetched once per id (concurrent callers share the request),
 * so screens may ask for the same pseudo as often as they like.
 */
@Injectable({ providedIn: 'root' })
export class AccountDirectory {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly t = inject(I18n).t;

  private readonly accounts = new Map<string, Observable<PublicAccountDto>>();
  private readonly pseudos = new Map<string, PseudoEntry>();

  get(id: string): Observable<PublicAccountDto> {
    let account = this.accounts.get(id);
    if (account === undefined) {
      account = getAccountById(this.http, this.rootUrl, { id }).pipe(
        map((response) => response.body),
        // shareReplay resets on error, so a failed lookup is retried by the next caller.
        shareReplay({ bufferSize: 1, refCount: false }),
      );
      this.accounts.set(id, account);
    }
    return account;
  }

  /**
   * « … » while loading, « Compte supprimé » once anonymized; the same signal for the same id,
   * in the reader's language. Safe to call from a template: no `toSignal`, which refuses to run in
   * a reactive context. A failed lookup shows « Compte inconnu » and is retried by a call made
   * 30 s or more later, on the same signal.
   */
  pseudo(id: string): Signal<string> {
    return this.entry(id).name;
  }

  /**
   * The marks of an account (founder first, then its plan badge), from the same cached lookup as
   * `pseudo`. Empty while loading and when the lookup failed; the same signal for the same id.
   */
  badges(id: string): Signal<AccountBadgeKind[]> {
    return this.entry(id).badges;
  }

  private entry(id: string): PseudoEntry {
    let entry = this.pseudos.get(id);
    if (entry === undefined) {
      const known = signal<Known>({ kind: 'loading' });
      entry = {
        known,
        name: computed(() => this.nameOf(known())),
        badges: computed(() => badgesOf(known())),
        failedAt: null,
      };
      this.pseudos.set(id, entry);
      this.load(id, entry);
    } else if (entry.failedAt !== null && Date.now() - entry.failedAt >= RETRY_COOLDOWN_MS) {
      this.load(id, entry);
    }
    return entry;
  }

  private nameOf(known: Known): string {
    const t = this.t();
    switch (known.kind) {
      case 'found':
        return displayName(known.account, t);
      case 'failed':
        return t.account.names.unknown;
      default:
        return t.account.names.loading;
    }
  }

  private load(id: string, entry: PseudoEntry): void {
    entry.failedAt = null;
    // A cached lookup emits during `subscribe`, i.e. inside whatever reactive context asked for the
    // pseudo: writing a signal there throws (NG0600), so a synchronous emission is deferred.
    let subscribing = true;
    const apply = (update: () => void): void => {
      if (subscribing) {
        queueMicrotask(update);
      } else {
        update();
      }
    };
    this.get(id).subscribe({
      next: (account) => apply(() => entry.known.set({ kind: 'found', account })),
      error: () =>
        apply(() => {
          entry.failedAt = Date.now();
          entry.known.set({ kind: 'failed' });
        }),
    });
    subscribing = false;
  }
}
