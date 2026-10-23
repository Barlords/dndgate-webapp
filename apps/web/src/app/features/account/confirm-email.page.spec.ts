import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideApi } from '../../core/api/api.providers';
import { authInterceptor } from '../../core/auth/auth.interceptor';
import { TokenStore } from '../../core/auth/token-store';
import { AuthService } from '../../core/auth/auth.service';
import { NotifierService } from '../../core/errors/notifier.service';
import { ConfirmEmailPage } from './confirm-email.page';

describe('ConfirmEmailPage', () => {
  let backend: HttpTestingController;

  function setup(query: Record<string, string>, accessToken?: string) {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideApi(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
        },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    if (accessToken) {
      TestBed.inject(TokenStore).save({ token: accessToken, refreshToken: 'r1' });
    }
    const fixture = TestBed.createComponent(ConfirmEmailPage);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  const link = { email: 'new@b.example', code: 'c0de' };
  const confirmUrl = (url: string) => /accounts\/email\/confirm$/.test(url);

  afterEach(() => backend.verify());

  it('confirm-email handles 204, 400 and 409', () => {
    let page = setup(link);
    const request = backend.expectOne((r) => r.method === 'POST' && confirmUrl(r.url));
    expect(request.request.body).toEqual({ email: 'new@b.example', code: 'c0de' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    page.fixture.detectChanges();
    expect(page.root.textContent).toContain('Ta nouvelle adresse est confirmée.');

    TestBed.resetTestingModule();
    page = setup(link);
    backend
      .expectOne((r) => confirmUrl(r.url))
      .flush({}, { status: 400, statusText: 'Bad Request' });
    page.fixture.detectChanges();
    expect(page.root.textContent).toContain("Ce lien n'est plus valable.");

    TestBed.resetTestingModule();
    page = setup(link);
    backend.expectOne((r) => confirmUrl(r.url)).flush({}, { status: 409, statusText: 'Conflict' });
    page.fixture.detectChanges();
    expect(page.root.textContent).toContain('Cette adresse est déjà utilisée par un autre compte.');
  });

  it('reloads the account after success when signed in', async () => {
    localStorage.clear();
    const { fixture } = setup(link);
    const reload = vi.spyOn(TestBed.inject(AuthService), 'reloadAccount');
    // Sign in by loading a profile.
    const auth = TestBed.inject(AuthService);
    auth.reloadAccount().subscribe();
    backend.expectOne((r) => r.method === 'GET').flush({ id: 1, pseudo: 'Gandalf' });
    reload.mockClear();
    backend
      .expectOne((r) => confirmUrl(r.url))
      .flush(null, { status: 204, statusText: 'No Content' });
    backend.expectOne((r) => r.method === 'GET').flush({ id: 1, pseudo: 'Gandalf' });
    fixture.detectChanges();
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('sends no Authorization header even when signed in', () => {
    const { fixture } = setup(link, 'access');
    const request = backend.expectOne((r) => confirmUrl(r.url));
    expect(TestBed.inject(TokenStore).accessToken()).toBe('access');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
  });

  it('does not reload the account when signed out', () => {
    const { fixture } = setup(link);
    backend
      .expectOne((r) => confirmUrl(r.url))
      .flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    backend.expectNone((r) => r.method === 'GET');
  });

  it('shows the invalid-link message without calling the API when parameters are missing', () => {
    const { root } = setup({ code: 'x' });
    backend.expectNone(() => true);
    expect(root.textContent).toContain("Ce lien n'est plus valable.");
  });

  it('reports an unexpected failure', () => {
    const { fixture, root } = setup(link);
    backend
      .expectOne((r) => confirmUrl(r.url))
      .flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(root.textContent).toContain('Réessaie plus tard.');
    expect(TestBed.inject(NotifierService).toasts().length).toBe(1);
  });
});
