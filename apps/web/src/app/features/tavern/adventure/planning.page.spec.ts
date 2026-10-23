import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { AvailabilityDto } from '../../../core/api/generated/models/availability-dto';
import { adventure, setupTavern, TavernTestbed } from '../tavern.testing';
import { Req } from './notes.testing';

describe('PlanningPage', () => {
  let tavern: TavernTestbed;

  const planningUrl = (r: Req) =>
    r.method === 'GET' && /\/planning$/.test(r.url) && r.params.get('adventureId') === 'a1';
  const votesUrl = (r: Req) => r.method === 'GET' && /\/planning\/p1\/availability$/.test(r.url);
  const accounts = (r: Req) => /\/accounts\/id\//.test(r.url);
  const planning = (dates = ['2026-11-14', '2026-11-21', '2026-11-28']) => ({
    id: 'p1',
    adventureId: 'a1',
    propositionDates: dates,
  });
  const vote = (accountId: string, dates: string[]): AvailabilityDto => ({
    id: `v-${accountId}`,
    planningId: 'p1',
    accountId,
    availabilityDates: dates,
  });

  async function open(overrides: Partial<AdventureDto> = {}) {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/planning');
    tavern.backend
      .expectOne((r) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url))
      .flush(adventure({ creatorId: 'master', players: ['me', 'p1'], ...overrides }));
    await tavern.render();
  }

  async function answerPlanning(body: object | null, votes?: AvailabilityDto[]) {
    const request = tavern.backend.expectOne(planningUrl);
    if (body === null) {
      request.flush(null, { status: 404, statusText: 'Not Found' });
    } else {
      request.flush(body);
    }
    await tavern.render();
    if (votes) {
      tavern.backend.expectOne(votesUrl).flush(votes);
      await tavern.render();
      tavern.backend.match(accounts).forEach((r) => r.flush({ pseudo: 'Alice' }));
      await tavern.render();
    }
  }

  const main = () => tavern.main();
  const boxes = () => [...main().querySelectorAll<HTMLInputElement>('.choices input')];
  const rows = () => [...main().querySelectorAll('tbody tr')];

  afterEach(() => {
    tavern.backend.match(accounts).forEach((r) => r.flush({ pseudo: 'Alice' }));
    tavern.backend.verify();
  });

  it('master proposes dates when there is no planning', async () => {
    await open({ creatorId: 'me', players: ['p1'] });
    await answerPlanning(null);
    expect(main().textContent).toContain('Aucune date proposée');
    tavern.button('Proposer des dates').click();
    await tavern.render();

    tavern.button('Enregistrer les dates').click();
    await tavern.render();
    expect(main().textContent).toContain('Choisis au moins une date.');
    tavern.backend.expectNone((r) => r.method === 'POST');

    const days = [...main().querySelectorAll<HTMLButtonElement>('.day:not(:disabled)')];
    const picked = [days[3].dataset['date']!, days[1].dataset['date']!];
    days[3].click();
    await tavern.render();
    days[1].click();
    await tavern.render();
    tavern.button('Enregistrer les dates').click();
    const post = tavern.backend.expectOne((r) => r.method === 'POST' && /\/planning$/.test(r.url));
    expect(post.request.body).toEqual({ adventureId: 'a1', propositionDates: picked.sort() });
    post.flush(planning(picked));
    await tavern.render();
    tavern.backend.expectOne(votesUrl).flush([]);
    tavern.backend.match(accounts);
    await tavern.render();
    expect(main().querySelector('dg-date-picker')).toBeNull();
    expect(main().querySelectorAll('.proposed li').length).toBe(2);
  });

  it('the master changes the proposed dates with put', async () => {
    await open({ creatorId: 'me', players: ['p1'] });
    await answerPlanning(planning(), [vote('p1', ['2026-11-14'])]);
    tavern.button('Modifier les dates').click();
    await tavern.render();
    const day = main().querySelector<HTMLButtonElement>('[data-date="2026-11-21"]')!;
    day.click();
    await tavern.render();
    tavern.button('Enregistrer les dates').click();
    const put = tavern.backend.expectOne((r) => r.method === 'PUT' && /\/planning$/.test(r.url));
    expect(put.request.body).toEqual({
      adventureId: 'a1',
      propositionDates: ['2026-11-14', '2026-11-28'],
    });
    put.flush(planning(['2026-11-14', '2026-11-28']));
    await tavern.render();
    tavern.backend.expectOne(votesUrl).flush([vote('p1', ['2026-11-14'])]);
    await tavern.render();
    expect(main().querySelectorAll('.proposed li').length).toBe(2);
  });

  it('voting replaces my availability', async () => {
    await open();
    await answerPlanning(planning(), [vote('me', ['2026-11-14']), vote('p1', ['2026-11-21'])]);
    expect(boxes().map((b) => b.checked)).toEqual([true, false, false]);
    boxes()[0].click();
    boxes()[2].click();
    await tavern.render();
    tavern.button('Enregistrer mes disponibilités').click();
    const put = tavern.backend.expectOne(
      (r) => r.method === 'PUT' && /\/planning\/availability$/.test(r.url),
    );
    expect(put.request.body).toEqual({ planningId: 'p1', propositionDates: ['2026-11-28'] });
    put.flush(vote('me', ['2026-11-28']));
    await tavern.render();
    tavern.backend
      .expectOne(votesUrl)
      .flush([vote('me', ['2026-11-28']), vote('p1', ['2026-11-21'])]);
    await tavern.render();
    expect(boxes().map((b) => b.checked)).toEqual([false, false, true]);
  });

  it('asks for at least one date before voting', async () => {
    await open();
    await answerPlanning(planning(), []);
    tavern.button('Enregistrer mes disponibilités').click();
    await tavern.render();
    expect(main().textContent).toContain('Coche au moins une date.');
    tavern.backend.expectNone((r) => r.method === 'PUT');
  });

  it('vote table totals each date and highlights the best', async () => {
    await open();
    await answerPlanning(planning(), [
      vote('me', ['2026-11-14', '2026-11-21']),
      vote('p1', ['2026-11-21']),
      vote('master', ['2026-11-21', '2026-11-28']),
    ]);
    expect(main().querySelector('thead')!.textContent).toContain('sam. 14 nov. 2026');
    expect(rows().length).toBe(3);
    const totals = [...main().querySelectorAll('tfoot td')].map((c) =>
      c.firstChild!.textContent!.trim(),
    );
    expect(totals).toEqual(['1', '3', '1']);
    const best = [...main().querySelectorAll('tfoot td')].map((c) => c.classList.contains('best'));
    expect(best).toEqual([false, true, false]);
    expect(main().querySelectorAll('thead th.best').length).toBe(1);
    expect(rows()[0].querySelector('th')!.textContent).toContain('Alice');
  });

  it('a visitor of a public adventure sees the dates but no votes', async () => {
    await open({ players: ['p1'] });
    await answerPlanning(planning());
    tavern.backend.expectNone(votesUrl);
    expect(main().querySelectorAll('.proposed li').length).toBe(3);
    expect(main().textContent).toContain('sam. 14 nov. 2026');
    expect(main().querySelector('table')).toBeNull();
    expect(tavern.button('Enregistrer mes disponibilités')).toBeUndefined();
    expect(tavern.button('Modifier les dates')).toBeUndefined();
  });

  it('players have nothing to propose when there is no planning', async () => {
    await open();
    await answerPlanning(null);
    expect(main().textContent).toContain('Aucune date proposée');
    expect(tavern.button('Proposer des dates')).toBeUndefined();
  });

  it('offers a retry when the planning cannot be loaded', async () => {
    await open();
    tavern.backend.expectOne(planningUrl).flush(null, { status: 500, statusText: 'Server Error' });
    await tavern.render();
    expect(main().textContent).toContain('Impossible de charger le planning');
    tavern.button('Réessayer').click();
    tavern.backend.expectOne(planningUrl).flush(null, { status: 404, statusText: 'Not Found' });
    await tavern.render();
    expect(main().textContent).toContain('Aucune date proposée');
  });

  it('keeps the vote form when saving fails', async () => {
    await open();
    await answerPlanning(planning(), []);
    boxes()[1].click();
    await tavern.render();
    tavern.button('Enregistrer mes disponibilités').click();
    tavern.backend
      .expectOne((r) => r.method === 'PUT' && /\/planning\/availability$/.test(r.url))
      .flush(null, { status: 500, statusText: 'Server Error' });
    await tavern.render();
    expect(boxes()[1].checked).toBe(true);
  });
});
