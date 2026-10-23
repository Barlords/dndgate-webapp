import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { provideApi } from '../../core/api/api.providers';
import { AuthService } from '../../core/auth/auth.service';
import { NotifierService } from '../../core/errors/notifier.service';
import { ConfirmService } from '../../shared/ui/confirm-dialog/confirm.service';
import { ProfilePage } from './profile.page';

describe('ProfilePage', () => {
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;
  let auth: AuthService;

  async function setup(account: object = { id: '1', pseudo: 'Gandalf', email: 'g@x.fr' }) {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi(), provideRouter([])],
    });
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    auth.reloadAccount().subscribe();
    backend.expectOne((r) => r.method === 'GET' && /accounts\/me$/.test(r.url)).flush(account);
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  function type(root: HTMLElement, selector: string, value: string) {
    const input = root.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function form(root: HTMLElement, index: number) {
    return root.querySelectorAll('form')[index];
  }

  const patch = () => backend.expectOne((r) => r.method === 'PATCH' && /accounts$/.test(r.url));

  afterEach(() => backend.verify());

  it('profile shows the pending e-mail', async () => {
    const { root } = await setup({
      id: '1',
      pseudo: 'Gandalf',
      email: 'g@x.fr',
      pendingEmail: 'new@x.fr',
    });
    expect(root.querySelector('.pending')?.textContent).toContain(
      'en attente de confirmation : new@x.fr',
    );
  });

  it('profile shows the pending e-mail returned by the change', async () => {
    const { fixture, root } = await setup();
    expect(root.querySelector('.pending')).toBeNull();
    type(root, '#identity-form input[formControlName="email"]', 'new@x.fr');
    type(root, '#identity-form input[formControlName="currentPassword"]', 'secret-pass');
    form(root, 0).dispatchEvent(new Event('submit'));
    patch().flush({ id: '1', pseudo: 'Gandalf', email: 'g@x.fr', pendingEmail: 'new@x.fr' });
    backend.expectOne((r) => r.method === 'GET' && /accounts\/me$/.test(r.url)).flush({});
    fixture.detectChanges();
    expect(root.querySelector('.pending')?.textContent).toContain('new@x.fr');
  });

  it('profile sends currentPassword with every change', async () => {
    const { root } = await setup();
    type(root, '#identity-form input[formControlName="pseudo"]', ' Mithrandir ');
    type(root, '#identity-form input[formControlName="currentPassword"]', 'secret-pass');
    form(root, 0).dispatchEvent(new Event('submit'));
    const identity = patch();
    expect(identity.request.body).toEqual({
      currentPassword: 'secret-pass',
      pseudo: 'Mithrandir',
    });
    identity.flush({ id: '1', pseudo: 'Mithrandir', email: 'g@x.fr' });
    backend.expectOne((r) => r.method === 'GET' && /accounts\/me$/.test(r.url)).flush({});

    type(root, '#password-form input[formControlName="newPassword"]', 'new-password');
    type(root, '#password-form input[formControlName="confirmation"]', 'new-password');
    type(root, '#password-form input[formControlName="currentPassword"]', 'secret-pass');
    form(root, 1).dispatchEvent(new Event('submit'));
    const password = patch();
    expect(password.request.body).toEqual({
      currentPassword: 'secret-pass',
      newPassword: 'new-password',
    });
    password.flush({ id: '1', pseudo: 'Mithrandir', email: 'g@x.fr' });
  });

  it('profile refuses a change without the current password', async () => {
    const { fixture, root } = await setup();
    type(root, '#identity-form input[formControlName="pseudo"]', 'Mithrandir');
    form(root, 0).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    backend.expectNone(() => true);
    expect(root.textContent).toContain('Ce champ est obligatoire.');
  });

  it('profile has nothing to send when nothing changed', async () => {
    const { root } = await setup();
    type(root, '#identity-form input[formControlName="currentPassword"]', 'secret-pass');
    form(root, 0).dispatchEvent(new Event('submit'));
    backend.expectNone(() => true);
    expect(TestBed.inject(NotifierService).toasts()[0].message).toBe('Rien à modifier.');
  });

  it('a wrong password on a change is shown on the field and keeps the session', async () => {
    const { fixture, root } = await setup();
    type(root, '#identity-form input[formControlName="pseudo"]', 'Mithrandir');
    type(root, '#identity-form input[formControlName="currentPassword"]', 'nope-nope');
    form(root, 0).dispatchEvent(new Event('submit'));
    patch().flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(root.textContent).toContain('Mot de passe incorrect.');
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('a pseudo already taken is shown on the pseudo field', async () => {
    const { fixture, root } = await setup();
    type(root, '#identity-form input[formControlName="pseudo"]', 'Saruman');
    type(root, '#identity-form input[formControlName="currentPassword"]', 'secret-pass');
    form(root, 0).dispatchEvent(new Event('submit'));
    patch().flush({}, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(root.textContent).toContain('Ce pseudo est déjà pris.');
  });

  it('changing the password ends the session', async () => {
    const { root } = await setup();
    const forget = vi.spyOn(auth, 'forgetSession');
    type(root, '#password-form input[formControlName="newPassword"]', 'new-password');
    type(root, '#password-form input[formControlName="confirmation"]', 'new-password');
    type(root, '#password-form input[formControlName="currentPassword"]', 'secret-pass');
    form(root, 1).dispatchEvent(new Event('submit'));
    patch().flush({ id: '1', pseudo: 'Gandalf', email: 'g@x.fr' });
    expect(forget).toHaveBeenCalled();
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('Reconnecte-toi');
  });

  it('profile checks the password confirmation', async () => {
    const { fixture, root } = await setup();
    type(root, '#password-form input[formControlName="newPassword"]', 'new-password');
    type(root, '#password-form input[formControlName="confirmation"]', 'other-password');
    type(root, '#password-form input[formControlName="currentPassword"]', 'secret-pass');
    form(root, 1).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    backend.expectNone(() => true);
    expect(root.textContent).toContain('Les deux mots de passe ne correspondent pas.');
  });

  it('signs out of this device or of every device', async () => {
    const { root } = await setup();
    const buttons = Array.from(root.querySelectorAll('dg-button button'));
    const named = (label: string) =>
      buttons.find((b) => b.textContent?.trim() === label) as HTMLButtonElement;
    named('Se déconnecter de partout').click();
    backend.expectOne((r) => r.method === 'POST' && /logout-all$/.test(r.url)).flush(null);
    expect(navigate).toHaveBeenCalledWith('/');
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('signs out of this device only', async () => {
    const { root } = await setup();
    const button = Array.from(root.querySelectorAll('dg-button button')).find(
      (b) => b.textContent?.trim() === 'Se déconnecter',
    ) as HTMLButtonElement;
    button.click();
    backend.expectOne((r) => r.method === 'POST' && /logout$/.test(r.url)).flush(null);
    expect(navigate).toHaveBeenCalledWith('/');
  });

  describe('delete account', () => {
    async function openDelete(answer: string | null) {
      const result = await setup();
      const typeToConfirm = vi
        .spyOn(TestBed.inject(ConfirmService), 'typeToConfirm')
        .mockReturnValue(of(answer));
      const button = Array.from(result.root.querySelectorAll('dg-button button')).find(
        (b) => b.textContent?.trim() === 'Supprimer mon compte',
      ) as HTMLButtonElement;
      button.click();
      return { ...result, typeToConfirm };
    }

    const deletion = () =>
      backend.expectOne((r) => r.method === 'DELETE' && /accounts$/.test(r.url));

    it('deletes the account, forgets the session and goes home', async () => {
      const { typeToConfirm } = await openDelete(' my pass ');
      expect(typeToConfirm.mock.calls[0][0]).toMatchObject({ inputType: 'password' });
      const request = deletion();
      expect(request.request.body).toEqual({ confirmation: ' my pass ' });
      request.flush(null, { status: 204, statusText: 'No Content' });
      expect(auth.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledWith('/');
      expect(TestBed.inject(NotifierService).toasts()[0].message).toBe(
        'Ton compte a été supprimé.',
      );
    });

    it('delete account stays logged in on 403', async () => {
      const { fixture, root } = await openDelete('wrong');
      deletion().flush({}, { status: 403, statusText: 'Forbidden' });
      fixture.detectChanges();
      expect(root.querySelector('[role="alert"]')?.textContent).toContain(
        'Mot de passe incorrect.',
      );
      expect(auth.isAuthenticated()).toBe(true);
      expect(navigate).not.toHaveBeenCalled();
    });

    it('reports another failure with a toast', async () => {
      await openDelete('right');
      deletion().flush({}, { status: 500, statusText: 'Server Error' });
      expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('Erreur du serveur');
    });

    it('does nothing when the reader cancels', async () => {
      await openDelete(null);
      backend.expectNone(() => true);
    });
  });
});
