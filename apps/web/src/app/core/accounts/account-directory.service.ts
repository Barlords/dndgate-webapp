import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal, Signal, WritableSignal } from '@angular/core';
import { map, Observable, shareReplay } from 'rxjs';
import { ApiConfiguration } from '../api/generated/api-configuration';
import { getAccountById } from '../api/generated/functions';
import { PublicAccountDto } from '../api/generated/models/public-account-dto';

export const PSEUDO_LOADING = '…';
export const PSEUDO_DELETED = 'Compte supprimé';
/** A failed lookup is not retried sooner than this: templates read `pseudo()` on every render. */
export const RETRY_COOLDOWN_MS = 30_000;
export const PSEUDO_UNKNOWN = 'Compte inconnu';

/** Display name of an account: its pseudo, or « Compte supprimé » once anonymized. */
export function displayName(account: PublicAccountDto): string {
  return account.anonymized ? PSEUDO_DELETED : (account.pseudo ?? PSEUDO_UNKNOWN);
}

interface PseudoEntry {
  name: WritableSignal<string>;
  /** When the last lookup failed, or null while loading or loaded. */
  failedAt: number | null;
}

/**
 * Who an account id is. Accounts are fetched once per id (concurrent callers share the request),
 * so screens may ask for the same pseudo as often as they like.
 */
@Injectable({ providedIn: 'root' })
export class AccountDirectory {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;

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
   * « … » while loading, « Compte supprimé » once anonymized; the same signal for the same id.
   * Safe to call from a template: no `toSignal`, which refuses to run in a reactive context.
   * A failed lookup shows « Compte inconnu » and is retried by a call made 30 s or more later,
   * on the same signal.
   */
  pseudo(id: string): Signal<string> {
    let entry = this.pseudos.get(id);
    if (entry === undefined) {
      entry = { name: signal(PSEUDO_LOADING), failedAt: null };
      this.pseudos.set(id, entry);
      this.load(id, entry);
    } else if (entry.failedAt !== null && Date.now() - entry.failedAt >= RETRY_COOLDOWN_MS) {
      this.load(id, entry);
    }
    return entry.name.asReadonly();
  }

  private load(id: string, entry: PseudoEntry): void {
    entry.failedAt = null;
    this.get(id).subscribe({
      next: (account) => entry.name.set(displayName(account)),
      error: () => {
        entry.failedAt = Date.now();
        entry.name.set(PSEUDO_UNKNOWN);
      },
    });
  }
}
