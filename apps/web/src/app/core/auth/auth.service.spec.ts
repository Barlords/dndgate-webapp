import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Injector } from '@angular/core';
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

  it('restore forgets a rejected refresh token', async () => {
    tokens.save({ token: 'x', refreshToken: 'dead' });
    const done = auth.restore();

    backend
      .expectOne('/dndgate-api/accounts/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await done;

    expect(tokens.refreshToken()).toBeNull();
  });

  it.each([0, 403, 429, 503])(
    'restore keeps the token when the refresh answers %s',
    async (status) => {
      tokens.save({ token: 'x', refreshToken: 'r1' });
      const done = auth.restore();

      const call = backend.expectOne('/dndgate-api/accounts/refresh');
      if (status === 0) {
        call.error(new ProgressEvent('error'));
      } else {
        call.flush(null, { status, statusText: 'Unavailable' });
      }
      await done;

      expect(tokens.refreshToken()).toBe('r1');
    },
  );

  describe('across tabs', () => {
    const tab = () => {
      const injector = Injector.create({
        parent: TestBed.inject(Injector),
        providers: [
          { provide: TokenStore, useClass: TokenStore },
          { provide: AuthService, useClass: AuthService },
        ],
      });
      return injector.get(AuthService);
    };

    afterEach(() => Reflect.deleteProperty(navigator, 'locks'));

    it('never presents the same refresh token twice when tabs refresh at once', async () => {
      // Grants the next waiter the moment the holder's callback settles, like the real lock.
      const waiting: (() => void)[] = [];
      let held = false;
      const grant = () => {
        const next = waiting.shift();
        held = next !== undefined;
        next?.();
      };
      Object.defineProperty(navigator, 'locks', {
        configurable: true,
        value: {
          request: (_name: string, callback: () => Promise<unknown>) =>
            new Promise((resolve, reject) => {
              const start = () =>
                callback().then(
                  (value) => {
                    grant();
                    resolve(value);
                  },
                  (error) => {
                    grant();
                    reject(error);
                  },
                );
              if (held) {
                waiting.push(start);
              } else {
                held = true;
                start();
              }
            }),
        },
      });
      tokens.save({ token: 'a1', refreshToken: 'r1' });
      const first = firstValueFrom(tab().refreshAccessToken());
      const second = firstValueFrom(tab().refreshAccessToken());

      const call1 = await vi.waitFor(() => backend.expectOne('/dndgate-api/accounts/refresh'));
      expect(call1.request.body).toEqual({ refreshToken: 'r1' });
      call1.flush({ token: 'a2', refreshToken: 'r2' });
      await first;

      const call2 = await vi.waitFor(() => backend.expectOne('/dndgate-api/accounts/refresh'));
      expect(call2.request.body).toEqual({ refreshToken: 'r2' });
      call2.flush({ token: 'a3', refreshToken: 'r3' });
      await second;
    });

    it('still refreshes when navigator.locks is unavailable', async () => {
      Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined });
      tokens.save({ token: 'a1', refreshToken: 'r1' });
      const done = firstValueFrom(auth.refreshAccessToken());

      backend.expectOne('/dndgate-api/accounts/refresh').flush({ token: 'a2', refreshToken: 'r2' });

      expect(await done).toBe('a2');
    });
  });
});
