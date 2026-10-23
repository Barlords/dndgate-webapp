import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Location } from '@angular/common';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Navigation, provideRouter, Router } from '@angular/router';
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

  const backButton = (root: HTMLElement) =>
    [...root.querySelectorAll<HTMLButtonElement>('dg-button button')].find(
      (button) => button.textContent?.trim() === 'Retour',
    )!;

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

  /** The password the reader types in the dialog asked on saving the identity; null: cancelled. */
  function answerPassword(password: string | null) {
    return vi.spyOn(TestBed.inject(ConfirmService), 'typeToConfirm').mockReturnValue(of(password));
  }

  it('lays out identity and danger zone on the left, password and sessions on the right', async () => {
    const { root } = await setup();
    const columns = [...root.querySelectorAll('.profile > .profile__column')];
    const titles = (column: Element) =>
      [...column.querySelectorAll('h2')].map((title) => title.textContent?.trim());
    expect(columns.map(titles)).toEqual([
      ['Identité', 'Zone dangereuse'],
      ['Mot de passe', 'Sessions'],
    ]);
    // The password form asks for the current password first.
    expect(
      [...root.querySelectorAll('#password-form input')].map((input) =>
        input.getAttribute('formControlName'),
      ),
    ).toEqual(['currentPassword', 'newPassword', 'confirmation']);
    // The identity form has no password field: it is asked on saving.
    expect(root.querySelector('#identity-form input[type="password"]')).toBeNull();
    expect(root.querySelector('.dg-account__card--wide')).not.toBeNull();
  });

  it('profile shows the pending e-mail returned by the change', async () => {
    const { fixture, root } = await setup();
    answerPassword('secret-pass');
    expect(root.querySelector('.pending')).toBeNull();
    type(root, '#identity-form input[formControlName="email"]', 'new@x.fr');
    form(root, 0).dispatchEvent(new Event('submit'));
    patch().flush({ id: '1', pseudo: 'Gandalf', email: 'g@x.fr', pendingEmail: 'new@x.fr' });
    backend.expectOne((r) => r.method === 'GET' && /accounts\/me$/.test(r.url)).flush({});
    fixture.detectChanges();
    expect(root.querySelector('.pending')?.textContent).toContain('new@x.fr');
  });

  it('profile sends currentPassword with every change', async () => {
    const { root } = await setup();
    const ask = answerPassword('secret-pass');
    type(root, '#identity-form input[formControlName="pseudo"]', ' Mithrandir ');
    form(root, 0).dispatchEvent(new Event('submit'));
    expect(ask.mock.calls[0][0]).toMatchObject({
      inputType: 'password',
      confirmLabel: 'Enregistrer',
    });
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

  it('sends nothing when the password dialog is cancelled', async () => {
    const { root } = await setup();
    answerPassword(null);
    type(root, '#identity-form input[formControlName="pseudo"]', 'Mithrandir');
    form(root, 0).dispatchEvent(new Event('submit'));
    backend.expectNone(() => true);
  });

  it('profile has nothing to send when nothing changed, without asking the password', async () => {
    const { root } = await setup();
    const ask = answerPassword('secret-pass');
    form(root, 0).dispatchEvent(new Event('submit'));
    backend.expectNone(() => true);
    expect(ask).not.toHaveBeenCalled();
    expect(TestBed.inject(NotifierService).toasts()[0].message).toBe('Rien à modifier.');
  });

  it('a wrong password on a change is shown in the section and keeps the session', async () => {
    const { fixture, root } = await setup();
    answerPassword('nope-nope');
    type(root, '#identity-form input[formControlName="pseudo"]', 'Mithrandir');
    form(root, 0).dispatchEvent(new Event('submit'));
    patch().flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(root.querySelector('.profile__identity [role="alert"]')?.textContent).toBe(
      'Mot de passe incorrect.',
    );
    expect(auth.isAuthenticated()).toBe(true);

    // Saving again starts afresh.
    answerPassword('secret-pass');
    form(root, 0).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(root.querySelector('.profile__identity [role="alert"]')).toBeNull();
    patch().flush({ id: '1', pseudo: 'Mithrandir', email: 'g@x.fr' });
    backend.expectOne((r) => r.method === 'GET' && /accounts\/me$/.test(r.url)).flush({});
  });

  it('a pseudo already taken is shown on the pseudo field', async () => {
    const { fixture, root } = await setup();
    answerPassword('secret-pass');
    type(root, '#identity-form input[formControlName="pseudo"]', 'Saruman');
    form(root, 0).dispatchEvent(new Event('submit'));
    patch().flush({}, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(root.textContent).toContain('Ce pseudo est déjà pris.');
  });

  it('a wrong current password on the password change is shown on its field', async () => {
    const { fixture, root } = await setup();
    type(root, '#password-form input[formControlName="newPassword"]', 'new-password');
    type(root, '#password-form input[formControlName="confirmation"]', 'new-password');
    type(root, '#password-form input[formControlName="currentPassword"]', 'nope-nope');
    form(root, 1).dispatchEvent(new Event('submit'));
    patch().flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(root.querySelector('#password-form')?.textContent).toContain('Mot de passe incorrect.');
    expect(root.querySelector('.profile__identity [role="alert"]')).toBeNull();
  });

  it('goes back to the page the reader came from', async () => {
    const { root } = await setup();
    const router = TestBed.inject(Router);
    // The reader came here from another page of the app.
    Object.defineProperty(router, 'lastSuccessfulNavigation', {
      get: () => signal({ previousNavigation: {} } as Navigation),
    });
    const back = vi.spyOn(TestBed.inject(Location), 'back').mockImplementation(() => undefined);
    backButton(root).click();
    expect(back).toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('goes home when the page was opened directly', async () => {
    const { root } = await setup();
    const back = vi.spyOn(TestBed.inject(Location), 'back');
    expect(root.textContent).not.toContain('Retour à la taverne');
    backButton(root).click();
    expect(back).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith('/');
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
