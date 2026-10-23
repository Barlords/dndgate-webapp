import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import {
  catchError,
  defer,
  finalize,
  firstValueFrom,
  map,
  Observable,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';
import { ApiConfiguration } from '../api/generated/api-configuration';
import { getMyProfile, login, logout, logoutAll, refresh } from '../api/generated/functions';
import { AccountDto } from '../api/generated/models/account-dto';
import { TokenStore } from './token-store';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly tokens = inject(TokenStore);

  private readonly accountState = signal<AccountDto | null>(null);
  readonly account = this.accountState.asReadonly();
  readonly isAuthenticated = computed(() => this.accountState() !== null);

  private inFlightRefresh: Observable<string> | null = null;

  /** Runs at startup; never rejects, so the app boots even when the API is down. */
  async restore(): Promise<void> {
    if (this.tokens.refreshToken() === null) {
      return;
    }
    try {
      await firstValueFrom(this.refreshAccessToken().pipe(switchMap(() => this.reloadAccount())));
    } catch {
      // Offline or expired session: stay logged out, show nothing.
    }
  }

  login(pseudo: string, password: string): Observable<void> {
    return login(this.http, this.rootUrl, { body: { pseudo, password } }).pipe(
      tap((response) => this.tokens.save(response.body)),
      switchMap(() => this.reloadAccount()),
      map(() => undefined),
    );
  }

  logout(): Observable<void> {
    return this.endSession(logout(this.http, this.rootUrl));
  }

  logoutAll(): Observable<void> {
    return this.endSession(logoutAll(this.http, this.rootUrl));
  }

  /**
   * Refresh tokens are single-use: replaying one revokes the whole session. While a refresh is
   * in flight every caller therefore shares the same Observable.
   */
  refreshAccessToken(): Observable<string> {
    if (this.inFlightRefresh === null) {
      const refreshToken = this.tokens.refreshToken();
      if (refreshToken === null) {
        return throwError(() => new Error('No refresh token'));
      }
      this.inFlightRefresh = defer(() =>
        refresh(this.http, this.rootUrl, { body: { refreshToken } }),
      ).pipe(
        map((response) => {
          this.tokens.save(response.body);
          return response.body.token ?? '';
        }),
        finalize(() => (this.inFlightRefresh = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.inFlightRefresh;
  }

  reloadAccount(): Observable<AccountDto> {
    return getMyProfile(this.http, this.rootUrl).pipe(
      map((response) => response.body),
      tap((account) => this.accountState.set(account)),
    );
  }

  /** Drops the local session without any network call. */
  forgetSession(): void {
    this.tokens.clear();
    this.accountState.set(null);
  }

  private endSession(call: Observable<unknown>): Observable<void> {
    return call.pipe(
      catchError(() => of(null)),
      tap(() => this.forgetSession()),
      map(() => undefined),
    );
  }
}
