import { adventure, page, setupTavern, TavernTestbed } from './tavern.testing';

describe('MyAdventuresPage', () => {
  let tavern: TavernTestbed;

  const asPlayer = (r: { url: string }) => /adventures\/as-player/.test(r.url);
  const asMaster = (r: { url: string }) => /adventures\/as-master/.test(r.url);

  async function open(player = [adventure()], master = [adventure()]) {
    tavern = await setupTavern();
    await tavern.go('/tavern/mine');
    tavern.backend.expectOne(asPlayer).flush(page(player));
    tavern.backend.expectOne(asMaster).flush(page(master));
    await tavern.render();
  }

  const names = () =>
    [...tavern.main().querySelectorAll('.card__name')].map((n) => n.textContent?.trim());

  afterEach(() => tavern.backend.verify());

  it('my adventures merges player and master lists without duplicates', async () => {
    await open(
      [adventure({ id: 'x', name: 'Zeta' }), adventure({ id: 'both', name: 'Beta' })],
      [adventure({ id: 'both', name: 'Beta' }), adventure({ id: 'm', name: 'Alpha' })],
    );
    expect(names()).toEqual(['Alpha', 'Beta', 'Zeta']);
    const roles = [...tavern.main().querySelectorAll('.seal--role')].map((r) => r.textContent);
    expect(roles).toEqual(['MJ', 'MJ', 'Joueur']);
  });

  it('requests every page with the maximum size', async () => {
    tavern = await setupTavern();
    await tavern.go('/tavern/mine');
    const first = tavern.backend.expectOne(asPlayer);
    expect(first.request.params.get('size')).toBe('100');
    first.flush(page([adventure({ id: 'x', name: 'Zeta' })], 2));
    tavern.backend.expectOne(asMaster).flush(page([]));
    await tavern.render();
    const second = tavern.backend.expectOne(asPlayer);
    expect(second.request.params.get('page')).toBe('1');
    second.flush(page([adventure({ id: 'y', name: 'Yota' })], 2, 1));
    await tavern.render();
    expect(names()).toEqual(['Yota', 'Zeta']);
  });

  it('shows every adventure, whatever its status', async () => {
    await open(
      [
        adventure({ id: '1', name: 'A', status: 'NOT_STARTED' }),
        adventure({ id: '2', name: 'B', status: 'ONGOING' }),
        adventure({ id: '3', name: 'C', status: 'FINISH' }),
        adventure({ id: '4', name: 'D', status: 'ABORT' }),
      ],
      [],
    );
    expect(names()).toEqual(['A', 'B', 'C', 'D']);
  });

  it('shows them as a table with the role of the reader', async () => {
    await open(
      [adventure({ id: 'p', name: 'Beta', status: 'FINISH' })],
      [adventure({ id: 'm', name: 'Alpha' })],
    );
    tavern.main().querySelector<HTMLButtonElement>('[aria-label="Afficher en tableau"]')!.click();
    await tavern.render();
    const rows = [...tavern.main().querySelectorAll('tbody tr')].map((tr) =>
      [...tr.querySelectorAll('td')].map((td) => td.textContent?.trim()),
    );
    expect(rows).toEqual([
      ['Alpha', 'MJ', 'Pas commencée', 'Paris (75001)', '1 / 4', 'Publique'],
      ['Beta', 'Joueur', 'Terminée', 'Paris (75001)', '1 / 4', 'Publique'],
    ]);
  });

  it('opens the creation of an adventure from its button', async () => {
    await open();
    tavern.main().querySelector<HTMLButtonElement>('[aria-label="Créer mon aventure"]')!.click();
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/new');
  });

  it('says so when there is nothing to show', async () => {
    await open([], []);
    expect(tavern.main().textContent).toContain('Tu ne joues ni ne mènes encore aucune aventure.');
  });

  it('the former tabs lead here', async () => {
    tavern = await setupTavern();
    await tavern.go('/tavern/mine/finished');
    expect(tavern.router.url).toBe('/tavern/mine');
    tavern.backend.match(() => true);
  });

  it('offers a retry when the lists cannot load', async () => {
    tavern = await setupTavern();
    await tavern.go('/tavern/mine');
    tavern.backend.expectOne(asPlayer).flush({}, { status: 500, statusText: 'Error' });
    await tavern.render();
    tavern.button('Réessayer').click();
    tavern.backend.expectOne(asPlayer).flush(page([adventure()]));
    // The master request of the failed attempt was cancelled and is still listed.
    tavern.backend.match(asMaster).at(-1)!.flush(page([]));
    await tavern.render();
    expect(names()).toEqual(['La mine perdue']);
  });
});
