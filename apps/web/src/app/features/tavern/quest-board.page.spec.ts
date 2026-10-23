import { adventure, page, setupTavern, TavernTestbed } from './tavern.testing';

describe('QuestBoardPage', () => {
  let tavern: TavernTestbed;

  const available = (r: { url: string; method: string }) =>
    r.method === 'GET' && /adventures\/available/.test(r.url);
  const join = (r: { url: string; method: string }) =>
    r.method === 'POST' && /adventures\/a1\/join/.test(r.url);

  async function open(content = [adventure()], totalPages = 1) {
    tavern = await setupTavern();
    await tavern.go('/tavern/quests');
    tavern.backend.expectOne(available).flush(page(content, totalPages));
    await tavern.render();
  }

  afterEach(() => tavern.backend.verify());

  it('lists the available quests with their seals', async () => {
    await open([adventure({ masterVacant: true, description: 'x'.repeat(300) })]);
    const card = tavern.main().querySelector('dg-adventure-card')!;
    expect(card.textContent).toContain('La mine perdue');
    expect(card.textContent).toContain('MJ recherché');
    expect(card.textContent).toContain('Pas commencée');
    expect(card.textContent).toContain('Paris (75001)');
    expect(card.textContent).toContain('1 / 4 joueurs');
    expect(card.textContent).toContain('Publique');
    expect(card.querySelector('.card__excerpt')!.textContent!.length).toBeLessThan(160);
  });

  it('asks the server for 20 quests per page and pages through them', async () => {
    tavern = await setupTavern();
    await tavern.go('/tavern/quests');
    const first = tavern.backend.expectOne(available);
    expect(first.request.params.get('size')).toBe('20');
    expect(first.request.params.get('page')).toBe('0');
    first.flush(page([adventure()], 3));
    await tavern.render();
    tavern.main().querySelector<HTMLButtonElement>('[aria-label="Page suivante"]')!.click();
    await tavern.render();
    const second = tavern.backend.expectOne(available);
    expect(second.request.params.get('page')).toBe('1');
    second.flush(page([adventure({ id: 'a2', name: 'Autre' })], 3, 1));
    await tavern.render();
    expect(tavern.main().textContent).toContain('Autre');
  });

  it('shows an empty message', async () => {
    await open([]);
    expect(tavern.main().textContent).toContain('Aucune quête');
  });

  it('quest board joins and opens the adventure', async () => {
    await open();
    tavern.button('Rejoindre').click();
    tavern.backend.expectOne(join).flush(adventure());
    await tavern.render();
    // The adventure page opens on its information tab and starts loading.
    expect(tavern.router.url).toBe('/tavern/adventures/a1/overview');
    tavern.backend.match(() => true);
  });

  it('reloads the board when the adventure cannot be joined any more', async () => {
    await open();
    tavern.button('Rejoindre').click();
    tavern.backend.expectOne(join).flush({}, { status: 409, statusText: 'Conflict' });
    await tavern.render();
    tavern.backend.expectOne(available).flush(page([]));
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/quests');
  });

  it('offers a retry when the board cannot load', async () => {
    tavern = await setupTavern();
    await tavern.go('/tavern/quests');
    tavern.backend.expectOne(available).flush({}, { status: 500, statusText: 'Error' });
    await tavern.render();
    expect(tavern.main().querySelector('[role="alert"]')).not.toBeNull();
    tavern.button('Réessayer').click();
    await tavern.render();
    tavern.backend.expectOne(available).flush(page([adventure()]));
    await tavern.render();
    expect(tavern.main().querySelector('dg-adventure-card')).not.toBeNull();
  });
});
