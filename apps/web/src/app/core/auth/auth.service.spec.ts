import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { provideApi } from '../api/api.providers';
import { AuthService } from './auth.service';
import { TokenStore } from './token-store';

describe('AuthService', () => {
  let auth: AuthService;
  let backend: HttpTestingController;
  let tokens: TokenStore;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    auth = TestBed.inject(AuthService);
    backend = TestBed.inject(HttpTestingController);
    tokens = TestBed.inject(TokenStore);
  });

  afterEach(() => backend.verify());

  it('restore loads the profile from a stored refresh token', async () => {
    tokens.save({ token: 'x', refreshToken: 'r1' });
    const done = auth.restore();

    backend.expectOne('/dndgate-api/accounts/refresh').flush({ token: 'a2', refreshToken: 'r2' });
    backend.expectOne('/dndgate-api/accounts/me').flush({ id: '1', pseudo: 'Gandalf' });
    await done;

    expect(auth.account()?.pseudo).toBe('Gandalf');
    expect(auth.isAuthenticated()).toBe(true);
    expect(tokens.accessToken()).toBe('a2');
  });

  it('restore stays logged out without token', async () => {
    await auth.restore();

    backend.expectNone('/dndgate-api/accounts/refresh');
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('restore never rejects when the API is down', async () => {
    tokens.save({ token: 'x', refreshToken: 'r1' });
    const done = auth.restore();

    backend.expectOne('/dndgate-api/accounts/refresh').error(new ProgressEvent('error'));
    await expect(done).resolves.toBeUndefined();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('login saves tokens and loads the account', async () => {
    const done = firstValueFrom(auth.login('gandalf', 'pw'));

    const call = backend.expectOne('/dndgate-api/accounts/login');
    expect(call.request.body).toEqual({ pseudo: 'gandalf', password: 'pw' });
    call.flush({ token: 'a1', refreshToken: 'r1' });
    backend.expectOne('/dndgate-api/accounts/me').flush({ id: '1', pseudo: 'Gandalf' });
    await done;

    expect(tokens.accessToken()).toBe('a1');
    expect(tokens.refreshToken()).toBe('r1');
    expect(auth.account()?.id).toBe('1');
  });

  it('logout clears the session even if the call fails', async () => {
    tokens.save({ token: 'a1', refreshToken: 'r1' });
    const done = firstValueFrom(auth.logout());

    backend
      .expectOne('/dndgate-api/accounts/logout')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await done;

    expect(tokens.accessToken()).toBeNull();
    expect(tokens.refreshToken()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('logoutAll clears the session', async () => {
    tokens.save({ token: 'a1', refreshToken: 'r1' });
    const done = firstValueFrom(auth.logoutAll());

    backend.expectOne('/dndgate-api/accounts/logout-all').flush(null);
    await done;

    expect(tokens.refreshToken()).toBeNull();
  });

  it('shares one refresh between simultaneous callers', async () => {
    tokens.save({ token: 'a1', refreshToken: 'r1' });
    const first = firstValueFrom(auth.refreshAccessToken());
    const second = firstValueFrom(auth.refreshAccessToken());

    backend.expectOne('/dndgate-api/accounts/refresh').flush({ token: 'a2', refreshToken: 'r2' });

    expect(await first).toBe('a2');
    expect(await second).toBe('a2');
  });

  it('starts a new refresh once the previous one has settled', async () => {
    tokens.save({ token: 'a1', refreshToken: 'r1' });
    const first = firstValueFrom(auth.refreshAccessToken());
    backend.expectOne('/dndgate-api/accounts/refresh').flush({ token: 'a2', refreshToken: 'r2' });
    await first;

    const second = firstValueFrom(auth.refreshAccessToken());
    const call = backend.expectOne('/dndgate-api/accounts/refresh');
    expect(call.request.body).toEqual({ refreshToken: 'r2' });
    call.flush({ token: 'a3', refreshToken: 'r3' });
    expect(await second).toBe('a3');
  });

  it('fails the refresh without a refresh token', async () => {
    await expect(firstValueFrom(auth.refreshAccessToken())).rejects.toThrow();
  });
});
