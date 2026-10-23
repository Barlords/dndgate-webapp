import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideApi } from '../api/api.providers';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { TokenStore } from './token-store';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let tokens: TokenStore;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    localStorage.clear();
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideApi(),
        { provide: Router, useValue: { navigateByUrl: navigate, url: '/adventures/7' } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    tokens = TestBed.inject(TokenStore);
    tokens.save({ token: 'old', refreshToken: 'r1' });
  });

  afterEach(() => backend.verify());

  /** A session whose account is already loaded, as after a normal startup. */
  function signedIn(): void {
    TestBed.inject(AuthService).reloadAccount().subscribe();
    backend.expectOne('/dndgate-api/accounts/me').flush({ id: '1', pseudo: 'Gandalf' });
  }

  it('adds the bearer only to API calls', () => {
    http.get('/dndgate-api/things').subscribe();
    http.get('https://www.dnd5eapi.co/api/spells').subscribe();

    expect(backend.expectOne('/dndgate-api/things').request.headers.get('Authorization')).toBe(
      'Bearer old',
    );
    expect(
      backend.expectOne('https://www.dnd5eapi.co/api/spells').request.headers.has('Authorization'),
    ).toBe(false);
  });

  it('refreshes once for concurrent 401s', () => {
    signedIn();
    const results: string[] = [];
    http.get<string>('/dndgate-api/a').subscribe((v) => results.push(v));
    http.get<string>('/dndgate-api/b').subscribe((v) => results.push(v));

    backend.expectOne('/dndgate-api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/dndgate-api/b').flush(null, { status: 401, statusText: 'Unauthorized' });

    const refresh = backend.expectOne('/dndgate-api/accounts/refresh');
    expect(refresh.request.method).toBe('POST');
    expect(refresh.request.body).toEqual({ refreshToken: 'r1' });
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush({ token: 'new', refreshToken: 'r2' });

    const a = backend.expectOne('/dndgate-api/a');
    const b = backend.expectOne('/dndgate-api/b');
    expect(a.request.headers.get('Authorization')).toBe('Bearer new');
    expect(b.request.headers.get('Authorization')).toBe('Bearer new');
    a.flush('A');
    b.flush('B');
    expect(results).toEqual(['A', 'B']);
    expect(tokens.refreshToken()).toBe('r2');
  });

  it('retries with the current token when a stale 401 arrives after a refresh', () => {
    http.get('/dndgate-api/a').subscribe();
    const stale = backend.expectOne('/dndgate-api/a');
    tokens.save({ token: 'newer', refreshToken: 'r2' });

    stale.flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone('/dndgate-api/accounts/refresh');
    expect(backend.expectOne('/dndgate-api/a').request.headers.get('Authorization')).toBe(
      'Bearer newer',
    );
  });

  it('logs out and redirects when the refresh fails', () => {
    const errors: number[] = [];
    http.get('/dndgate-api/a').subscribe({ error: (e) => errors.push(e.status) });
    http.get('/dndgate-api/b').subscribe({ error: (e) => errors.push(e.status) });
    backend.expectOne('/dndgate-api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/dndgate-api/b').flush(null, { status: 401, statusText: 'Unauthorized' });

    backend
      .expectOne('/dndgate-api/accounts/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(errors).toEqual([401, 401]);
    expect(tokens.accessToken()).toBeNull();
    expect(tokens.refreshToken()).toBeNull();
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith('/login?returnUrl=%2Fadventures%2F7');
  });

  it.each([400, 401])('forgets the session and redirects when the refresh answers %s', (code) => {
    http.get('/dndgate-api/a').subscribe({ error: () => undefined });
    backend.expectOne('/dndgate-api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend
      .expectOne('/dndgate-api/accounts/refresh')
      .flush(null, { status: code, statusText: 'No' });

    expect(tokens.refreshToken()).toBeNull();
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it.each([403, 429, 503])(
    'keeps the session and propagates the refresh error when the refresh answers %s',
    (code) => {
      let status = 0;
      http.get('/dndgate-api/a').subscribe({ error: (e) => (status = e.status) });
      backend.expectOne('/dndgate-api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
      backend
        .expectOne('/dndgate-api/accounts/refresh')
        .flush(null, { status: code, statusText: 'No' });

      expect(status).toBe(code);
      expect(tokens.refreshToken()).toBe('r1');
      expect(navigate).not.toHaveBeenCalled();
    },
  );

  it('forgets the session when the refresh cannot reach the server', () => {
    http.get('/dndgate-api/a').subscribe({ error: () => undefined });
    backend.expectOne('/dndgate-api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/dndgate-api/accounts/refresh').error(new ProgressEvent('error'));

    expect(tokens.refreshToken()).toBeNull();
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('reloads the account after a refresh when none is loaded', () => {
    http.get('/dndgate-api/a').subscribe();
    backend.expectOne('/dndgate-api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/dndgate-api/accounts/refresh').flush({ token: 'new', refreshToken: 'r2' });
    backend.expectOne('/dndgate-api/accounts/me').flush({ id: '1', pseudo: 'Gandalf' });
    backend.expectOne('/dndgate-api/a').flush('A');

    expect(TestBed.inject(AuthService).account()?.pseudo).toBe('Gandalf');
  });

  it('ignores a failure of that account reload', () => {
    let result = '';
    http.get<string>('/dndgate-api/a').subscribe((v) => (result = v));
    backend.expectOne('/dndgate-api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/dndgate-api/accounts/refresh').flush({ token: 'new', refreshToken: 'r2' });
    backend.expectOne('/dndgate-api/accounts/me').flush(null, { status: 503, statusText: 'No' });
    backend.expectOne('/dndgate-api/a').flush('A');

    expect(result).toBe('A');
  });

  it('does not refresh on a 403', () => {
    let status = 0;
    http.get('/dndgate-api/a').subscribe({ error: (e) => (status = e.status) });
    backend.expectOne('/dndgate-api/a').flush(null, { status: 403, statusText: 'Forbidden' });

    backend.expectNone('/dndgate-api/accounts/refresh');
    expect(status).toBe(403);
    expect(tokens.refreshToken()).toBe('r1');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('never refreshes the refresh call itself', () => {
    let status = 0;
    http.post('/dndgate-api/accounts/refresh', {}).subscribe({ error: (e) => (status = e.status) });
    const call = backend.expectOne('/dndgate-api/accounts/refresh');
    expect(call.request.headers.has('Authorization')).toBe(false);
    call.flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone('/dndgate-api/accounts/refresh');
    expect(status).toBe(401);
  });

  it('never refreshes the login call', () => {
    let status = 0;
    http.post('/dndgate-api/accounts/login', {}).subscribe({ error: (e) => (status = e.status) });
    const call = backend.expectOne('/dndgate-api/accounts/login');
    expect(call.request.headers.has('Authorization')).toBe(false);
    call.flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
  });

  it('does not loop when the replayed call is rejected again', () => {
    signedIn();
    let status = 0;
    http.get('/dndgate-api/a').subscribe({ error: (e) => (status = e.status) });
    backend.expectOne('/dndgate-api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/dndgate-api/accounts/refresh').flush({ token: 'new', refreshToken: 'r2' });
    backend.expectOne('/dndgate-api/a').flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone('/dndgate-api/accounts/refresh');
    expect(status).toBe(401);
  });

  describe('public routes', () => {
    it.each([
      '/dndgate-api/accounts',
      '/dndgate-api/accounts/login',
      '/dndgate-api/accounts/refresh',
      '/dndgate-api/accounts/verify',
      '/dndgate-api/accounts/email/confirm',
      '/dndgate-api/accounts/verification-email',
    ])('sends no bearer and never refreshes on POST %s', (url) => {
      let status = 0;
      http.post(url, {}).subscribe({ error: (e) => (status = e.status) });
      const request = backend.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush(null, { status: 401, statusText: 'Unauthorized' });
      backend.expectNone('/dndgate-api/accounts/refresh');
      expect(status).toBe(401);
    });

    it('still sends the bearer on PATCH and DELETE /accounts', () => {
      http.patch('/dndgate-api/accounts', {}).subscribe();
      http.request('DELETE', '/dndgate-api/accounts', { body: {} }).subscribe();
      const requests = backend.match('/dndgate-api/accounts');
      expect(requests.map((r) => r.request.headers.get('Authorization'))).toEqual([
        'Bearer old',
        'Bearer old',
      ]);
    });
  });
});
