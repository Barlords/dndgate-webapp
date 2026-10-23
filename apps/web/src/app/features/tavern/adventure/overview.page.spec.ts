import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { NotifierService } from '../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { adventure, setupTavern, TavernTestbed } from '../tavern.testing';

interface Req {
  method: string;
  url: string;
}

describe('OverviewPage', () => {
  let tavern: TavernTestbed;

  const get = (r: Req) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url);
  const patch = (r: Req) => r.method === 'PATCH' && /\/adventures\/a1$/.test(r.url);
  const put = (r: Req) => r.method === 'PUT' && /\/adventures\/a1\/status$/.test(r.url);
  const accounts = (r: Req) => /\/accounts\/id\//.test(r.url);

  async function open(overrides: Partial<AdventureDto> = {}, tab = 'overview') {
    tavern = await setupTavern();
    await tavern.go(`/tavern/adventures/a1/${tab}`);
    tavern.backend.expectOne(get).flush(adventure(overrides));
    await tavern.render();
    tavern.backend.match(accounts).forEach((r) => r.flush({ id: 'master', pseudo: 'Gandalf' }));
    // The planning tab asks for the planning; there is none here.
    tavern.backend
      .match((r) => r.method === 'GET' && /\/planning$/.test(r.url))
      .forEach((r) => r.flush(null, { status: 404, statusText: 'Not Found' }));
    await tavern.render();
  }

  /** The text of the tabs the reader sees. */
  const tabs = () =>
    [...tavern.main().querySelectorAll('.tabs a')].map((a) => a.textContent?.trim());
  const buttons = () =>
    [...tavern.main().querySelectorAll('button')].map((b) => b.textContent?.trim());
  const nameInput = () =>
    tavern.main().querySelector<HTMLInputElement>('input[formControlName="name"]')!;
  const type = (value: string) => {
    nameInput().value = value;
    nameInput().dispatchEvent(new Event('input'));
  };

  afterEach(() => tavern.backend.verify());

  it('shows the adventure and names the breadcrumb', async () => {
    await open();
    expect(tavern.main().textContent).toContain('La mine perdue');
    expect(tavern.main().textContent).toContain('Gandalf');
    expect(tavern.main().textContent).toContain('1 / 4');
    expect(
      TestBed.inject(BreadcrumbService)
        .crumbs()
        .map((c) => c.label),
    ).toEqual(['Taverne', 'La mine perdue', 'Informations']);
  });

  it('the adventure address lands on the information tab', async () => {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1');
    tavern.backend.expectOne(get).flush(adventure());
    await tavern.render();
    tavern.backend.match(accounts);
    expect(tavern.router.url).toBe('/tavern/adventures/a1/overview');
  });

  it('shows a missing adventure with a link to the quest board', async () => {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/overview');
    tavern.backend.expectOne(get).flush({}, { status: 404, statusText: 'Not Found' });
    await tavern.render();
    expect(tavern.main().textContent).toContain('Cette aventure est introuvable ou privée.');
    expect(tavern.main().querySelector('a[href="/tavern/quests"]')).not.toBeNull();
  });

  it('a failed load can be retried', async () => {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/overview');
    tavern.backend.expectOne(get).flush({}, { status: 500, statusText: 'Server Error' });
    await tavern.render();
    tavern.button('Réessayer').click();
    tavern.backend.expectOne(get).flush({}, { status: 500, statusText: 'Server Error' });
  });

  it('master sees only allowed status transitions', async () => {
    await open({ creatorId: 'me', status: 'NOT_STARTED' });
    expect(buttons()).toContain('Démarrer l’aventure');
    expect(buttons()).toContain('Abandonner l’aventure');
    expect(buttons()).not.toContain('Terminer l’aventure');
  });

  it('a closed adventure offers no transition and no edition', async () => {
    await open({ creatorId: 'me', status: 'FINISH' });
    expect(buttons()).not.toContain('Démarrer l’aventure');
    expect(buttons()).not.toContain('Terminer l’aventure');
    expect(buttons()).not.toContain('Enregistrer');
  });

  it('starting the adventure needs no confirmation', async () => {
    await open({ creatorId: 'me' });
    tavern.button('Démarrer l’aventure').click();
    const request = tavern.backend.expectOne(put);
    expect(request.request.body).toEqual({ status: 'ONGOING' });
    request.flush(adventure({ creatorId: 'me', status: 'ONGOING' }));
    await tavern.render();
    expect(tavern.main().textContent).toContain('En cours');
    expect(buttons()).toContain('Terminer l’aventure');
  });

  it('finishing asks for a confirmation and does nothing if refused', async () => {
    await open({ creatorId: 'me', status: 'ONGOING' });
    const confirm = vi
      .spyOn(TestBed.inject(ConfirmService), 'confirm')
      .mockReturnValueOnce(of(false))
      .mockReturnValueOnce(of(true));
    tavern.button('Terminer l’aventure').click();
    expect(confirm.mock.calls[0][0].message).toContain('irréversible');
    tavern.backend.expectNone(put);
    tavern.button('Abandonner l’aventure').click();
    expect(confirm.mock.calls[1][0].message).toContain('irréversible');
    expect(tavern.backend.expectOne(put).request.body).toEqual({ status: 'ABORT' });
  });

  it('a refused status change is shown and the adventure reloaded', async () => {
    await open({ creatorId: 'me' });
    tavern.button('Démarrer l’aventure').click();
    tavern.backend.expectOne(put).flush({}, { status: 409, statusText: 'Conflict' });
    tavern.backend.expectOne(get).flush(adventure({ creatorId: 'me', status: 'ONGOING' }));
    await tavern.render();
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('Conflit');
    expect(buttons()).toContain('Terminer l’aventure');
  });

  it('toggles the visibility', async () => {
    await open({ creatorId: 'me' });
    tavern.button('Rendre privée').click();
    const request = tavern.backend.expectOne(
      (r) => r.method === 'PUT' && /\/adventures\/a1\/visibility$/.test(r.url),
    );
    expect(request.request.body).toEqual({ visibility: 'PRIVATE' });
    request.flush(adventure({ creatorId: 'me', visibility: 'PRIVATE' }));
    await tavern.render();
    expect(buttons()).toContain('Rendre publique');
  });

  it('delete is disabled while ongoing', async () => {
    await open({ creatorId: 'me', status: 'ONGOING' });
    expect(tavern.button('Supprimer l’aventure').disabled).toBe(true);
    expect(tavern.main().textContent).toContain('ne peut pas être supprimée');
  });

  it('delete requires the adventure name', async () => {
    await open({ creatorId: 'me' });
    const typeToConfirm = vi
      .spyOn(TestBed.inject(ConfirmService), 'typeToConfirm')
      .mockReturnValue(of('La mine perdue'));
    tavern.button('Supprimer l’aventure').click();
    expect(typeToConfirm.mock.calls[0][0]).toMatchObject({
      expected: 'La mine perdue',
      inputType: 'text',
    });
    const request = tavern.backend.expectOne(
      (r) => r.method === 'DELETE' && /\/adventures\/a1$/.test(r.url),
    );
    expect(request.request.body).toEqual({ confirmation: 'La mine perdue' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/mine/ongoing');
    tavern.backend.match(() => true);
  });

  it('a cancelled deletion sends nothing', async () => {
    await open({ creatorId: 'me' });
    vi.spyOn(TestBed.inject(ConfirmService), 'typeToConfirm').mockReturnValue(of(null));
    tavern.button('Supprimer l’aventure').click();
  });

  it('saves the edition and keeps the form on what was saved', async () => {
    await open({ creatorId: 'me' });
    type('Nouvelle mine');
    tavern.button('Enregistrer').click();
    const request = tavern.backend.expectOne(patch);
    expect(request.request.body.name).toBe('Nouvelle mine');
    expect(request.request.body.visibility).toBeUndefined();
    request.flush(adventure({ creatorId: 'me', name: 'Nouvelle mine' }));
    await tavern.render();
    expect(nameInput().value).toBe('Nouvelle mine');
    expect(tavern.main().querySelector('h1')!.textContent).toBe('Nouvelle mine');
  });

  it('offers to reload on a 409 while editing', async () => {
    await open({ creatorId: 'me' });
    const notifier = TestBed.inject(NotifierService);
    type('Ma saisie');
    tavern.button('Enregistrer').click();
    tavern.backend.expectOne(patch).flush({}, { status: 409, statusText: 'Conflict' });
    await tavern.render();

    const toast = notifier.toasts()[0];
    expect(toast.action?.label).toBe('Recharger');
    // The input is kept and nothing is fetched until « Recharger » is clicked.
    expect(nameInput().value).toBe('Ma saisie');
    tavern.backend.expectNone(get);

    notifier.act(toast);
    tavern.backend.expectOne(get).flush(adventure({ creatorId: 'me', name: 'Version du serveur' }));
    await tavern.render();
    expect(nameInput().value).toBe('Version du serveur');
  });

  it('a refresh of the adventure does not wipe the input being typed', async () => {
    await open({ creatorId: 'me' });
    type('Ma saisie');
    tavern.button('Rendre privée').click();
    tavern.backend
      .expectOne((r) => r.method === 'PUT' && /visibility$/.test(r.url))
      .flush(adventure({ creatorId: 'me', visibility: 'PRIVATE' }));
    await tavern.render();
    expect(nameInput().value).toBe('Ma saisie');
  });

  it('the edit form has no visibility field and never sends one, even after a toggle', async () => {
    await open({ creatorId: 'me' });
    expect(tavern.main().querySelector('select')).toBeNull();
    tavern.button('Rendre privée').click();
    tavern.backend
      .expectOne((r) => r.method === 'PUT' && /visibility$/.test(r.url))
      .flush(adventure({ creatorId: 'me', visibility: 'PRIVATE' }));
    await tavern.render();
    expect(tavern.main().querySelector('select')).toBeNull();
    type('Autre nom');
    tavern.button('Enregistrer').click();
    const request = tavern.backend.expectOne(patch);
    expect('visibility' in request.request.body).toBe(false);
    request.flush(adventure({ creatorId: 'me', visibility: 'PRIVATE', name: 'Autre nom' }));
  });

  it('a visitor opening a participants-only tab by address lands on the overview', async () => {
    await open({ players: [] }, 'players');
    expect(tavern.router.url).toBe('/tavern/adventures/a1/overview');
    expect(tabs()).toEqual(['Informations', 'Planning']);
  });

  it('a visitor may open the planning tab', async () => {
    await open({ players: [] }, 'planning');
    expect(tavern.router.url).toBe('/tavern/adventures/a1/planning');
  });

  it('a participant stays on a participants-only tab', async () => {
    await open({ players: ['me'] }, 'players');
    expect(tavern.router.url).toBe('/tavern/adventures/a1/players');
  });

  it('shows the field errors of a 400', async () => {
    await open({ creatorId: 'me' });
    tavern.button('Enregistrer').click();
    tavern.backend
      .expectOne(patch)
      .flush(
        { status: 400, errors: [{ field: 'name', message: 'trop long' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    await tavern.render();
    expect(tavern.main().textContent).toContain('trop long');
  });

  it('shows another failure as a toast', async () => {
    await open({ creatorId: 'me' });
    tavern.button('Enregistrer').click();
    tavern.backend.expectOne(patch).flush({}, { status: 500, statusText: 'Server Error' });
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('Erreur du serveur');
  });

  it('visitor of a public adventure sees info and planning only', async () => {
    await open({ creatorId: 'master', visibility: 'PUBLIC', players: [] });
    expect(tabs()).toEqual(['Informations', 'Planning']);
    expect(buttons()).toEqual(['Rejoindre']);
  });

  it('a visitor joins the adventure and then sees every tab', async () => {
    await open({ players: [] });
    tavern.button('Rejoindre').click();
    tavern.backend
      .expectOne((r) => r.method === 'POST' && /\/adventures\/a1\/join$/.test(r.url))
      .flush(null, { status: 204, statusText: 'No Content' });
    tavern.backend.expectOne(get).flush(adventure({ players: ['me'] }));
    await tavern.render();
    expect(tabs()).toHaveLength(8);
    expect(buttons()).toContain('Quitter l’aventure');
    expect(buttons()).not.toContain('Rejoindre');
  });

  it('a started adventure cannot be joined', async () => {
    await open({ status: 'ONGOING', players: [] });
    expect(buttons()).not.toContain('Rejoindre');
  });

  it('a player sees every tab but no master control', async () => {
    await open({ players: ['me'] });
    expect(tabs()).toEqual([
      'Informations',
      'Joueurs',
      'Personnages',
      'Chronique',
      'Notes du MJ',
      'Planning',
      'Messagerie',
      'MJ',
    ]);
    expect(buttons()).toEqual(['Quitter l’aventure']);
  });

  it('a player leaves after confirming', async () => {
    await open({ players: ['me'] });
    const confirm = vi.spyOn(TestBed.inject(ConfirmService), 'confirm').mockReturnValue(of(true));
    tavern.button('Quitter l’aventure').click();
    expect(confirm).toHaveBeenCalled();
    tavern.backend
      .expectOne((r) => r.method === 'DELETE' && /\/adventures\/a1\/players\/me$/.test(r.url))
      .flush(adventure());
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/mine/ongoing');
    tavern.backend.match(() => true);
  });

  it('a vacant master seat is shown', async () => {
    await open({ masterVacant: true, creatorId: 'gone' });
    expect(tavern.main().textContent).toContain('Aucun MJ pour le moment.');
    expect(tavern.main().textContent).toContain('MJ absent');
  });

  it('placeholder tabs render', async () => {
    await open({ players: ['me'] }, 'messages');
    expect(tavern.main().textContent).toContain('Bientôt disponible.');
  });
});
