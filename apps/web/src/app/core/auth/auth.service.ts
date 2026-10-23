import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import {
  catchError,
  defer,
  finalize,
  firstValueFrom,
  from,
  map,
  Observable,
  of,
  shareReplay,
  Subject,
  switchMap,
  tap,
  throwError,
} from 'rxjs';
import { ApiConfiguration } from '../api/generated/api-configuration';
import { getMyProfile, login, logout, logoutAll, refresh } from '../api/generated/functions';
import { AccountDto } from '../api/generated/models/account-dto';
import { AuthDto } from '../api/generated/models/auth-dto';
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

  private readonly ended = new Subject<void>();
  /** Emits whenever the local session is dropped: forgotten, logged out or logged out everywhere. */
  readonly sessionEnded: Observable<void> = this.ended.asObservable();

  /** Runs at startup; never rejects, so the app boots even when the API is down. */
  async restore(): Promise<void> {
    if (this.tokens.refreshToken() === null) {
      return;
    }
    try {
      await firstValueFrom(this.refreshAccessToken().pipe(switchMap(() => this.reloadAccount())));
    } catch (error) {
      // A refresh token the server rejected is dead: drop it. Keep it when the API is merely
      // unreachable (status 0) or failing (5xx), so a later reload can still restore.
      if (error instanceof HttpErrorResponse && (error.status === 400 || error.status === 401)) {
        this.forgetSession();
      }
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
      this.inFlightRefresh = defer(() => this.refreshExclusively()).pipe(
        map((auth) => auth.token ?? ''),
        finalize(() => (this.inFlightRefresh = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.inFlightRefresh;
  }

  /**
   * Tabs share the refresh token through localStorage: serialise their refreshes, and read the
   * token only once the lock is held so a tab never presents a token another tab just used.
   */
  private refreshExclusively(): Observable<AuthDto> {
    const locks = typeof navigator === 'undefined' ? undefined : navigator.locks;
    return locks
      ? from(locks.request('dg.refresh', () => firstValueFrom(this.callRefresh())))
      : this.callRefresh();
  }

  private callRefresh(): Observable<AuthDto> {
    const refreshToken = this.tokens.refreshToken();
    if (refreshToken === null) {
      return throwError(() => new Error('No refresh token'));
    }
    // Saved here, inside the lock: the next tab must find the rotated token when it gets in.
    return refresh(this.http, this.rootUrl, { body: { refreshToken } }).pipe(
      map((response) => response.body),
      tap((auth) => this.tokens.save(auth)),
    );
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
    this.ended.next();
  }

  private endSession(call: Observable<unknown>): Observable<void> {
    return call.pipe(
      catchError(() => of(null)),
      tap(() => this.forgetSession()),
      map(() => undefined),
    );
  }
}
