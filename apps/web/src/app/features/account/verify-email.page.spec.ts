import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideApi } from '../../core/api/api.providers';
import { NotifierService } from '../../core/errors/notifier.service';
import { VerifyEmailPage } from './verify-email.page';

describe('VerifyEmailPage', () => {
  let backend: HttpTestingController;

  function setup(query: Record<string, string>) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
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
    const fixture = TestBed.createComponent(VerifyEmailPage);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  const link = { email: 'a@b.example', code: 'c0de' };

  afterEach(() => backend.verify());

  it('verify-email handles 204, 400 and 409', () => {
    // 204
    let page = setup(link);
    const request = backend.expectOne(
      (r) => r.method === 'POST' && /accounts\/verify$/.test(r.url),
    );
    expect(request.request.body).toEqual({ email: 'a@b.example', code: 'c0de' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    page.fixture.detectChanges();
    expect(page.root.textContent).toContain('Ton compte est validé.');
    expect(page.root.querySelector('dg-parchment a')?.getAttribute('href')).toBe('/login');

    // 400
    TestBed.resetTestingModule();
    page = setup(link);
    backend
      .expectOne((r) => /accounts\/verify$/.test(r.url))
      .flush({}, { status: 400, statusText: 'Bad Request' });
    page.fixture.detectChanges();
    expect(page.root.textContent).toContain("Ce lien n'est plus valable.");
    expect(page.root.querySelector<HTMLInputElement>('input')?.value).toBe('a@b.example');

    // 409
    TestBed.resetTestingModule();
    page = setup(link);
    backend
      .expectOne((r) => /accounts\/verify$/.test(r.url))
      .flush({}, { status: 409, statusText: 'Conflict' });
    page.fixture.detectChanges();
    expect(page.root.textContent).toContain(
      'Ce pseudo a été pris entre-temps : inscris-toi avec un autre.',
    );
    expect(page.root.querySelector('dg-parchment a')?.getAttribute('href')).toBe('/signup');
  });

  it('shows the invalid-link message without calling the API when parameters are missing', () => {
    const { root } = setup({ email: 'a@b.example' });
    backend.expectNone(() => true);
    expect(root.textContent).toContain("Ce lien n'est plus valable.");
  });

  it('resends the e-mail from the invalid-link form with a neutral message', () => {
    const { fixture, root } = setup({});
    const input = root.querySelector<HTMLInputElement>('input')!;
    input.value = 'x@y.example';
    input.dispatchEvent(new Event('input'));
    root.querySelector('form')!.dispatchEvent(new Event('submit'));
    const resend = backend.expectOne((r) => /verification-email$/.test(r.url));
    expect(resend.request.body).toEqual({ email: 'x@y.example' });
    resend.flush({}, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();
    expect(root.querySelector('[role="status"]')?.textContent).toContain(
      'un nouvel e-mail vient de partir',
    );
  });

  it('does not resend with an empty address', () => {
    const { fixture, root } = setup({});
    root.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    backend.expectNone(() => true);
    expect(root.textContent).toContain('Ce champ est obligatoire.');
  });

  it('reports an unexpected failure', () => {
    const { fixture, root } = setup(link);
    backend
      .expectOne((r) => /accounts\/verify$/.test(r.url))
      .flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(root.textContent).toContain('Réessaie plus tard.');
    expect(TestBed.inject(NotifierService).toasts().length).toBe(1);
  });
});
