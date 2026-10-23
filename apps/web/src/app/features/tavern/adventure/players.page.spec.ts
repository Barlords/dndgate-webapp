import { TestBed } from '@angular/core/testing';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { NotifierService } from '../../../core/errors/notifier.service';
import { adventure, setupTavern, TavernTestbed } from '../tavern.testing';

interface Req {
  method: string;
  url: string;
}

describe('PlayersPage', () => {
  let tavern: TavernTestbed;

  const invitation = (r: Req) => r.method === 'POST' && /\/invitations\/Elminster$/.test(r.url);

  async function open(overrides: Partial<AdventureDto> = {}) {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/players');
    tavern.backend
      .expectOne((r) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url))
      .flush(adventure({ creatorId: 'me', ...overrides }));
    await tavern.render();
    tavern.backend
      .match((r) => /\/accounts\/id\//.test(r.url))
      .forEach((r) => r.flush({ id: 'x', pseudo: `Pseudo ${r.request.url.split('/').pop()}` }));
    await tavern.render();
  }

  async function invite(pseudo = 'Elminster') {
    const input = tavern.main().querySelector<HTMLInputElement>('input')!;
    input.value = pseudo;
    input.dispatchEvent(new Event('input'));
    tavern.button('Inviter').click();
  }

  afterEach(() => tavern.backend.verify());

  it('lists the master first, then the players, with profile links', async () => {
    await open({ players: ['p1', 'p2'] });
    const items = [...tavern.main().querySelectorAll('.people li')];
    expect(items.map((i) => i.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      'MJPseudo me',
      'JoueurPseudo p1',
      'JoueurPseudo p2',
    ]);
    expect(items[1].querySelector('a')!.getAttribute('href')).toBe('/players/p1');
  });

  it('says so when nobody joined', async () => {
    await open({ players: [] });
    expect(tavern.main().textContent).toContain('Aucun joueur pour le moment.');
  });

  it('says so when the master seat is vacant', async () => {
    await open({ creatorId: 'gone', masterVacant: true, players: ['me'] });
    expect(tavern.main().textContent).toContain('Aucun MJ pour le moment.');
  });

  it('only the master of a new adventure can invite', async () => {
    await open({ status: 'ONGOING' });
    expect(tavern.main().textContent).not.toContain('Inviter un joueur');
  });

  it('a player cannot invite', async () => {
    await open({ creatorId: 'master', players: ['me'] });
    expect(tavern.main().textContent).not.toContain('Inviter un joueur');
  });

  it('invites by pseudo and confirms with a toast', async () => {
    await open();
    await invite();
    tavern.backend.expectOne(invitation).flush(null, { status: 202, statusText: 'Accepted' });
    await tavern.render();
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('Elminster');
    expect(tavern.main().querySelector('input')!.value).toBe('');
  });

  it('does not send an empty pseudo', async () => {
    await open();
    tavern.button('Inviter').click();
    tavern.backend.expectNone(invitation);
  });

  it('invite shows the retry delay on 429', async () => {
    await open();
    await invite();
    tavern.backend
      .expectOne(invitation)
      .flush({}, { status: 429, statusText: 'Too Many', headers: { 'Retry-After': '90' } });
    await tavern.render();
    expect(tavern.main().querySelector('[role="alert"]')!.textContent).toContain('90 s');
  });

  it('429 without a delay still explains', async () => {
    await open();
    await invite();
    tavern.backend.expectOne(invitation).flush({}, { status: 429, statusText: 'Too Many' });
    await tavern.render();
    expect(tavern.main().querySelector('[role="alert"]')!.textContent).toContain('plus tard');
  });

  it('explains a 409', async () => {
    await open();
    await invite();
    tavern.backend.expectOne(invitation).flush({}, { status: 409, statusText: 'Conflict' });
    await tavern.render();
    expect(tavern.main().querySelector('[role="alert"]')!.textContent).toContain('déjà');
  });

  it('shows another failure as a toast', async () => {
    await open();
    await invite();
    tavern.backend.expectOne(invitation).flush({}, { status: 404, statusText: 'Not Found' });
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('Invitation du joueur');
  });
});
