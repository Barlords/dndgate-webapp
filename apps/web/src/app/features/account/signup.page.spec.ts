import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideApi } from '../../core/api/api.providers';
import { SignupPage } from './signup.page';

describe('SignupPage', () => {
  let backend: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi(), provideRouter([])],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SignupPage);
    fixture.detectChanges();
    return fixture;
  }

  function fill(root: HTMLElement, values: Record<string, string>) {
    for (const [name, value] of Object.entries(values)) {
      const input = root.querySelector<HTMLInputElement>(`input[formControlName="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
  }

  const valid = {
    pseudo: 'Gandalf',
    email: 'gandalf@shire.example',
    password: 'you-shall-not-pass',
    confirmation: 'you-shall-not-pass',
  };

  function submit(fixture: ReturnType<typeof setup>) {
    (fixture.nativeElement as HTMLElement)
      .querySelector('form')!
      .dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  afterEach(() => backend.verify());

  it('signup rejects a password over 72 bytes', () => {
    const fixture = setup();
    const root = fixture.nativeElement as HTMLElement;
    // 25 x 'é' = 25 characters but 50 bytes, plus 23 ASCII = 73 bytes.
    const password = 'é'.repeat(25) + 'a'.repeat(23);
    expect(new TextEncoder().encode(password).length).toBe(73);
    fill(root, { ...valid, password, confirmation: password });
    submit(fixture);
    backend.expectNone(() => true);
    expect(root.textContent).toContain('72 octets');
  });

  it('signup accepts a password of exactly 72 bytes', () => {
    const fixture = setup();
    const password = 'é'.repeat(25) + 'a'.repeat(22);
    fill(fixture.nativeElement, { ...valid, password, confirmation: password });
    submit(fixture);
    backend
      .expectOne((r) => r.method === 'POST')
      .flush({ message: 'ok' }, { status: 202, statusText: 'Accepted' });
  });

  it('signup rejects a confirmation that differs', () => {
    const fixture = setup();
    const root = fixture.nativeElement as HTMLElement;
    fill(root, { ...valid, confirmation: 'something-else' });
    submit(fixture);
    backend.expectNone(() => true);
    expect(root.textContent).toContain('ne correspondent pas');
  });

  it('signup rejects an invalid pseudo', () => {
    const fixture = setup();
    const root = fixture.nativeElement as HTMLElement;
    fill(root, { ...valid, pseudo: 'a b' });
    submit(fixture);
    backend.expectNone(() => true);
    expect(root.textContent).toContain('Le format attendu n’est pas respecté.');
  });

  it('signup shows the check-your-mail step on 202', () => {
    const fixture = setup();
    const root = fixture.nativeElement as HTMLElement;
    fill(root, valid);
    submit(fixture);
    const request = backend.expectOne((r) => r.method === 'POST' && /\/accounts$/.test(r.url));
    expect(request.request.body).toEqual({
      pseudo: 'Gandalf',
      email: 'gandalf@shire.example',
      password: 'you-shall-not-pass',
    });
    request.flush({ message: 'ok' }, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();
    expect(root.textContent).toContain(
      'Dernière étape : ouvre le lien reçu par e-mail pour valider ton compte.',
    );
    expect(root.querySelector('form')).toBeNull();
  });

  it('signup resends the e-mail with a neutral message', () => {
    const fixture = setup();
    const root = fixture.nativeElement as HTMLElement;
    fill(root, valid);
    submit(fixture);
    backend
      .expectOne((r) => /\/accounts$/.test(r.url))
      .flush({ message: 'ok' }, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();
    root.querySelector('button')!.click();
    const resend = backend.expectOne((r) => /verification-email$/.test(r.url));
    expect(resend.request.body).toEqual({ email: 'gandalf@shire.example' });
    resend.flush({}, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();
    expect(root.querySelector('[role="status"]')?.textContent).toContain(
      'un nouvel e-mail vient de partir',
    );
  });

  it('signup marks the pseudo on 409', () => {
    const fixture = setup();
    const root = fixture.nativeElement as HTMLElement;
    fill(root, valid);
    submit(fixture);
    backend
      .expectOne((r) => /\/accounts$/.test(r.url))
      .flush({ title: 'Conflict' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(root.textContent).toContain('Ce pseudo est déjà pris.');
    expect(root.querySelector('form')).not.toBeNull();
  });

  it('signup puts server field errors on their fields on 400', () => {
    const fixture = setup();
    const root = fixture.nativeElement as HTMLElement;
    fill(root, valid);
    submit(fixture);
    backend
      .expectOne((r) => /\/accounts$/.test(r.url))
      .flush(
        { errors: [{ field: 'email', message: 'Adresse refusée.' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();
    expect(root.textContent).toContain('Adresse refusée.');
  });
});
