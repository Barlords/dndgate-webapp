import { AdventureDto } from '../../../../core/api/generated/models/adventure-dto';
import { adventure, setupTavern, TavernTestbed } from '../../tavern.testing';
import { character, stubObjectUrls } from './characters.testing';

interface Req {
  method: string;
  url: string;
  params: { get(name: string): string | null };
}

describe('CharacterListPage', () => {
  let tavern: TavernTestbed;

  const getAdventure = (r: Req) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url);
  const list = (r: Req) =>
    r.method === 'GET' && /\/game-characters$/.test(r.url) && r.params.get('adventureId') === 'a1';
  const pictures = (r: Req) => /\/game-characters\/[^/]+\/picture$/.test(r.url);
  const accounts = (r: Req) => /\/accounts\/id\//.test(r.url);

  async function open(overrides: Partial<AdventureDto> = {}) {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/characters');
    tavern.backend
      .expectOne(getAdventure)
      .flush(adventure({ players: ['me', 'p1'], ...overrides }));
    await tavern.render();
  }

  async function answer(
    characters = [character(), character({ id: 'c2', name: 'Elara', ownerId: 'me' })],
  ) {
    tavern.backend.expectOne(list).flush(characters);
    await tavern.render();
    tavern.backend.match(pictures).forEach((r) => r.flush(new Blob(['x'])));
    tavern.backend
      .match(accounts)
      .forEach((r) => r.flush({ id: 'x', pseudo: `Pseudo ${r.request.url.split('/').pop()}` }));
    await tavern.render();
  }

  beforeEach(() => stubObjectUrls());
  afterEach(() => {
    tavern.backend.verify();
    vi.unstubAllGlobals();
  });

  it('list shows the whole party', async () => {
    await open();
    await answer();
    const cards = [...tavern.main().querySelectorAll('.card')];
    expect(cards.map((c) => c.querySelector('.name')!.textContent?.trim())).toEqual([
      'Thorin',
      'Elara',
    ]);
    expect(cards[0].textContent).toContain('Nain');
    expect(cards[0].textContent).toContain('Guerrier');
    expect(cards[0].textContent).toContain('Pseudo p1');
    expect(cards[1].textContent).toContain('Pseudo me');
    expect(cards[0].getAttribute('href')).toBe('/tavern/adventures/a1/characters/c1');
    expect(cards[0].querySelector('img')!.getAttribute('src')).toMatch(/^blob:/);
  });

  it('shows a placeholder while the picture loads', async () => {
    await open();
    tavern.backend.expectOne(list).flush([character()]);
    await tavern.render();
    expect(tavern.main().querySelector('.placeholder dg-icon')).not.toBeNull();
    tavern.backend.match(pictures).forEach((r) => r.flush(new Blob(['x'])));
    tavern.backend.match(accounts).forEach((r) => r.flush({}));
    await tavern.render();
    expect(tavern.main().querySelector('.placeholder')).toBeNull();
  });

  it('keeps the placeholder when the picture fails', async () => {
    await open();
    tavern.backend.expectOne(list).flush([character()]);
    await tavern.render();
    tavern.backend
      .match(pictures)
      .forEach((r) => r.flush(new Blob(), { status: 404, statusText: 'Not Found' }));
    tavern.backend.match(accounts).forEach((r) => r.flush({}));
    await new Promise((resolve) => setTimeout(resolve));
    await tavern.render();
    expect(tavern.main().querySelector('.placeholder dg-icon')).not.toBeNull();
  });

  it('says so when nobody created a character', async () => {
    await open();
    await answer([]);
    expect(tavern.main().textContent).toContain('Aucun personnage pour le moment.');
  });

  it('a participant can create a character', async () => {
    await open();
    await answer();
    tavern.button('Créer un personnage').click();
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/adventures/a1/characters/new');
  });

  it('asks nothing of the API for someone who is not a participant', async () => {
    await open({ players: ['p1'], creatorId: 'master' });
    tavern.backend.expectNone(list);
    tavern.backend.match(accounts).forEach((r) => r.flush({}));
    expect(tavern.router.url).toBe('/tavern/adventures/a1/overview');
  });

  it('offers to retry when the list cannot be loaded', async () => {
    await open();
    tavern.backend.expectOne(list).flush({}, { status: 500, statusText: 'Server' });
    await tavern.render();
    expect(tavern.main().querySelector('[role="alert"]')!.textContent).toContain('Impossible');
    tavern.button('Réessayer').click();
    await answer([]);
    expect(tavern.main().textContent).toContain('Aucun personnage');
  });
});
