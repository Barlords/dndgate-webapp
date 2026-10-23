import { TestBed } from '@angular/core/testing';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { NotifierService } from '../../../core/errors/notifier.service';
import { adventure, setupTavern, TavernTestbed } from '../tavern.testing';

interface Req {
  urlWithParams: string;
  method: string;
  url: string;
}

describe('PlayersPage', () => {
  let tavern: TavernTestbed;

  const invitation = (r: Req) => r.method === 'POST' && /\/invitations\/Elminster$/.test(r.url);

  async function open(
    overrides: Partial<AdventureDto> = {},
    account: (id: string) => Record<string, unknown> = () => ({}),
  ) {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/players');
    tavern.backend
      .expectOne((r) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url))
      .flush(adventure({ creatorId: 'me', ...overrides }));
    await tavern.render();
    tavern.backend
      .match((r) => /\/accounts\/id\//.test(r.url))
      .forEach((r) =>
        r.flush({
          id: 'x',
          pseudo: `Pseudo ${r.request.url.split('/').pop()}`,
          ...account(r.request.url.split('/').pop()!),
        }),
      );
    await tavern.render();
  }

  async function invite(pseudo = 'Elminster') {
    const input = tavern.main().querySelector<HTMLInputElement>('input')!;
    input.value = pseudo;
    input.dispatchEvent(new Event('input'));
    tavern.button('Inviter').click();
  }

  const between = (id: string) => (r: Req) =>
    r.method === 'GET' &&
    /\/ratings\/between/.test(r.url) &&
    r.urlWithParams.includes(`targetedAccountId=${id}`);
  const mean = (id: string) => (r: Req) =>
    r.method === 'GET' &&
    /\/ratings\/mean/.test(r.url) &&
    r.urlWithParams.includes(`accountId=${id}`);
  const put = (r: Req) => r.method === 'PUT' && /\/ratings$/.test(r.url);

  /** Answers the rating lookups of the given ids: not rated yet (404) unless a rate is given. */
  async function answer(ids: Record<string, number | null>, average: number | null = null) {
    for (const [id, rate] of Object.entries(ids)) {
      const request = tavern.backend.expectOne(between(id));
      if (rate === null) {
        request.flush({}, { status: 404, statusText: 'Not Found' });
      } else {
        request.flush({ rate });
      }
      tavern.backend.expectOne(mean(id)).flush({ average, count: average === null ? 0 : 3 });
    }
    await tavern.render();
  }

  const text = () => tavern.main().textContent?.replace(/\s+/g, ' ');

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
    await open({ status: 'ONGOING', players: [] });
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

  describe('ratings', () => {
    it('rating is offered only on ongoing or finished adventures', async () => {
      await open({ creatorId: 'master', players: ['me', 'p1'], status: 'NOT_STARTED' });
      expect(text()).not.toContain('Ta note');
      tavern.backend.expectNone((r) => /\/ratings/.test(r.url));
    });

    it('is not offered to someone who is not a participant', async () => {
      await open({ creatorId: 'master', players: ['p1'], status: 'ONGOING' });
      expect(text()).not.toContain('Ta note');
      tavern.backend.expectNone((r) => /\/ratings/.test(r.url));
    });

    it('asks for the master and the other players, with their mean', async () => {
      await open({ creatorId: 'master', players: ['me', 'p1'], status: 'FINISH' });
      await answer({ master: 4, p1: null }, 3.5);
      expect(text()).toContain('Moyenne : 3,5 (3 avis)');
      const rows = tavern.main().querySelectorAll('.people li');
      expect(rows[0].querySelector('dg-star-rating')).not.toBeNull();
      expect(rows[1].querySelector('dg-star-rating')).toBeNull();
      expect(rows[2].querySelector('dg-star-rating')).not.toBeNull();
    });

    it('a 404 means not rated yet', async () => {
      await open({ creatorId: 'me', players: ['p1'], status: 'ONGOING' });
      await answer({ p1: null });
      const row = tavern.main().querySelectorAll('.people li')[1];
      expect(row.textContent).toContain('Pas encore d’avis');
      expect(row.querySelectorAll('[aria-checked="true"]').length).toBe(0);
    });

    it('no rating for myself or anonymized accounts', async () => {
      await open({ creatorId: 'master', players: ['me', 'p1'], status: 'ONGOING' }, (id) =>
        id === 'p1' ? { anonymized: true } : {},
      );
      await answer({ master: null });
      tavern.backend.expectNone((r) => /\/ratings/.test(r.url));
      expect(tavern.main().querySelectorAll('dg-star-rating').length).toBe(1);
    });

    it('no rating for a vacant master seat', async () => {
      await open({ creatorId: 'gone', masterVacant: true, players: ['me'], status: 'ONGOING' });
      tavern.backend.expectNone((r) => /\/ratings/.test(r.url));
    });

    it('a click puts the rating and refreshes the mean', async () => {
      await open({ creatorId: 'me', players: ['p1'], status: 'ONGOING' });
      await answer({ p1: null });
      tavern.main().querySelectorAll<HTMLButtonElement>('.people li button')[3].click();
      const request = tavern.backend.expectOne(put);
      expect(request.request.body).toEqual({ accountTargetedId: 'p1', rate: 4 });
      request.flush({ rate: 4 }, { status: 201, statusText: 'Created' });
      tavern.backend.expectOne(mean('p1')).flush({ average: 4, count: 1 });
      await tavern.render();
      expect(text()).toContain('Moyenne : 4,0 (1 avis)');
      expect(
        tavern
          .main()
          .querySelector('.people li:nth-child(2) [aria-checked="true"]')!
          .getAttribute('aria-label'),
      ).toBe('4 sur 5');
    });

    it('keeps the previous rate and explains when the API refuses', async () => {
      await open({ creatorId: 'me', players: ['p1'], status: 'ONGOING' });
      await answer({ p1: 2 }, 2);
      tavern.main().querySelectorAll<HTMLButtonElement>('.people li button')[4].click();
      tavern.backend.expectOne(put).flush({}, { status: 409, statusText: 'Conflict' });
      await tavern.render();
      expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('Évaluation');
      expect(
        tavern
          .main()
          .querySelector('.people li:nth-child(2) [aria-checked="true"]')!
          .getAttribute('aria-label'),
      ).toBe('2 sur 5');
    });

    it('toasts a lookup failure other than 404', async () => {
      await open({ creatorId: 'me', players: ['p1'], status: 'ONGOING' });
      tavern.backend.expectOne(between('p1')).flush({}, { status: 500, statusText: 'Error' });
      tavern.backend.expectOne(mean('p1')).flush({ average: null, count: 0 });
      await tavern.render();
      expect(TestBed.inject(NotifierService).toasts().length).toBe(1);
    });

    it('survives a failing mean lookup', async () => {
      await open({ creatorId: 'me', players: ['p1'], status: 'ONGOING' });
      tavern.backend.expectOne(between('p1')).flush({ rate: 3 });
      tavern.backend.expectOne(mean('p1')).flush({}, { status: 500, statusText: 'Error' });
      await tavern.render();
      expect(text()).not.toContain('Moyenne');
    });
  });
});
