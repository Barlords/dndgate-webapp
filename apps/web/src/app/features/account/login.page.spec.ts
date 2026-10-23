import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { provideApi } from '../../core/api/api.providers';
import { NotifierService } from '../../core/errors/notifier.service';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  function setup(query: Record<string, string> = {}) {
    localStorage.clear();
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
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    return fixture;
  }

  function answerLogin(
    status: number,
    body: object | null = null,
    headers: Record<string, string> = {},
  ) {
    const request = backend.expectOne((r) => r.method === 'POST' && /login$/.test(r.url));
    request.flush(body, { status, statusText: String(status), headers });
  }

  afterEach(() => backend.verify());

  async function loginWith(query: Record<string, string>) {
    const fixture = setup(query);
    const root = fixture.nativeElement as HTMLElement;
    const type = (selector: string, value: string) => {
      const input = root.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    type('input[formControlName="pseudo"]', 'Gandalf');
    type('input[formControlName="password"]', 'secret-pass');
    root.querySelector('form')!.dispatchEvent(new Event('submit'));
    return { fixture, root };
  }

  it('login goes to returnUrl', async () => {
    const { fixture } = await loginWith({ returnUrl: '/tavern/adventures/7' });
    answerLogin(200, { token: 'a', refreshToken: 'r' });
    backend.expectOne((r) => r.method === 'GET').flush({ id: 1, pseudo: 'Gandalf' });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith('/tavern/adventures/7');
  });

  it('login goes to the tavern without a returnUrl', async () => {
    const { fixture } = await loginWith({});
    answerLogin(200, { token: 'a', refreshToken: 'r' });
    backend.expectOne((r) => r.method === 'GET').flush({ id: 1, pseudo: 'Gandalf' });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith('/tavern');
  });

  it.each(['https://evil.example/x', '//evil.example/x', 'tavern'])(
    'login ignores an external returnUrl (%s)',
    async (returnUrl) => {
      const { fixture } = await loginWith({ returnUrl });
      answerLogin(200, { token: 'a', refreshToken: 'r' });
      backend.expectOne((r) => r.method === 'GET').flush({ id: 1, pseudo: 'Gandalf' });
      await fixture.whenStable();
      expect(navigate).toHaveBeenCalledWith('/tavern');
    },
  );

  it('login shows a wrong-credentials message on 401', async () => {
    const { fixture, root } = await loginWith({});
    answerLogin(401, { title: 'Unauthorized' });
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain(
      'Pseudo ou mot de passe incorrect.',
    );
    expect(TestBed.inject(NotifierService).toasts()).toEqual([]);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('login reports a throttled attempt with the delay', async () => {
    const { fixture } = await loginWith({});
    answerLogin(429, {}, { 'Retry-After': '30' });
    fixture.detectChanges();
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('30 s');
  });

  it('does not call the API while the form is incomplete', () => {
    const fixture = setup();
    (fixture.nativeElement as HTMLElement)
      .querySelector('form')!
      .dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    backend.expectNone(() => true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Ce champ est obligatoire.',
    );
  });
});
