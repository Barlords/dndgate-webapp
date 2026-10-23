import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal, Signal } from '@angular/core';
import { catchError, map, Observable, of, shareReplay } from 'rxjs';
import { ApiConfiguration } from '../api/generated/api-configuration';
import { getAccountById } from '../api/generated/functions';
import { PublicAccountDto } from '../api/generated/models/public-account-dto';

export const PSEUDO_LOADING = '…';
export const PSEUDO_DELETED = 'Compte supprimé';
export const PSEUDO_UNKNOWN = 'Compte inconnu';

/** Display name of an account: its pseudo, or « Compte supprimé » once anonymized. */
export function displayName(account: PublicAccountDto): string {
  return account.anonymized ? PSEUDO_DELETED : (account.pseudo ?? PSEUDO_UNKNOWN);
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
  private readonly pseudos = new Map<string, Signal<string>>();

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
   */
  pseudo(id: string): Signal<string> {
    let pseudo = this.pseudos.get(id);
    if (pseudo === undefined) {
      const state = signal(PSEUDO_LOADING);
      this.get(id)
        .pipe(
          map(displayName),
          catchError(() => of(PSEUDO_UNKNOWN)),
        )
        .subscribe((name) => state.set(name));
      pseudo = state.asReadonly();
      this.pseudos.set(id, pseudo);
    }
    return pseudo;
  }
}
