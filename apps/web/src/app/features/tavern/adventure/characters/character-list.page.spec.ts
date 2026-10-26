import { TestBed } from '@angular/core/testing';
import { AdventureDto } from '../../../../core/api/generated/models/adventure-dto';
import { NotifierService } from '../../../../core/errors/notifier.service';
import { AdventureEntitlementsDto } from '../../../../core/api/generated/models/adventure-entitlements-dto';
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

  async function open(
    overrides: Partial<AdventureDto> = {},
    entitlements?: AdventureEntitlementsDto,
  ) {
    tavern = await setupTavern(entitlements);
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

  describe('active character', () => {
    const activate = (r: Req) =>
      r.method === 'PUT' && /\/adventures\/a1\/players\/me\/active-character$/.test(r.url);
    const party = () => [
      character({ id: 'c1', name: 'Thorin', ownerId: 'p1', active: true }),
      character({ id: 'c2', name: 'Elara', ownerId: 'me', active: true }),
      character({ id: 'c3', name: 'Bran', ownerId: 'me', active: false }),
      character({ id: 'c5', name: 'Pip', ownerId: 'p1', active: false }),
      character({ id: 'c4', name: 'Garde', ownerId: 'me', active: false, npc: true }),
    ];
    const entries = () => [...tavern.main().querySelectorAll('.portraits > li')];
    const marked = () => entries().map((c) => c.textContent?.includes('Actif'));
    const switchButton = (entry: Element) =>
      [...entry.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Rendre actif');

    it('the_active_character_is_marked_and_can_be_switched', async () => {
      await open();
      await answer(party());
      expect(marked()).toEqual([true, true, false, false, false]);
      // Only the reader's own non-active player characters can be switched to.
      expect(entries().map((c) => !!switchButton(c))).toEqual([false, false, true, false, false]);

      switchButton(entries()[2])!.click();
      const request = tavern.backend.expectOne(activate);
      expect(request.request.body).toEqual({ characterId: 'c3' });
      request.flush(character({ id: 'c3', name: 'Bran', ownerId: 'me', active: true }));
      await tavern.render();

      expect(marked()).toEqual([true, false, true, false, false]);
      expect(entries().map((c) => !!switchButton(c))).toEqual([false, true, false, false, false]);
      expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('Bran');
    });

    it('a refused switch leaves the marks as they were', async () => {
      await open();
      await answer(party());
      switchButton(entries()[2])!.click();
      tavern.backend.expectOne(activate).flush({}, { status: 500, statusText: 'Server' });
      await tavern.render();
      expect(marked()).toEqual([true, true, false, false, false]);
      expect(TestBed.inject(NotifierService).toasts()[0].kind).toBe('error');
    });
  });

  describe('NPCs of the master', () => {
    const setVisibility = (r: Req) =>
      r.method === 'PUT' && /\/game-characters\/n[12]\/visibility$/.test(r.url);
    const withUsage: AdventureEntitlementsDto = {
      features: [],
      limits: { npcsPerAdventure: { used: 2, max: 5 } },
    };
    const cast = () => [
      character({ id: 'c1', name: 'Thorin', ownerId: 'p1', npc: false }),
      character({ id: 'n1', name: 'Garde', ownerId: 'me', npc: true, visibility: 'PRIVATE' }),
      character({ id: 'n2', name: 'Aubergiste', ownerId: 'me', npc: true, visibility: 'PUBLIC' }),
    ];
    const section = (title: string) =>
      [...tavern.main().querySelectorAll('section')].find(
        (s) => s.querySelector('h2')?.textContent?.trim() === title,
      );
    const names = (root: Element | undefined) =>
      [...(root?.querySelectorAll('.name') ?? [])].map((n) => n.textContent?.trim());
    const toggles = () => [
      ...tavern.main().querySelectorAll<HTMLButtonElement>('button[aria-pressed]'),
    ];

    it('the_master_sees_npcs_with_their_visibility_toggle', async () => {
      await open({ creatorId: 'me', players: ['p1'] }, withUsage);
      await answer(cast());
      expect(names(section('Personnages des joueurs'))).toEqual(['Thorin']);
      expect(names(section('PNJ'))).toEqual(['Garde', 'Aubergiste']);
      expect(section('PNJ')!.textContent).toContain('2 / 5 PNJ');
      expect(toggles().map((t) => t.textContent!.replace(/\s+/g, ' ').trim())).toEqual([
        'Privé Garde',
        'Public Aubergiste',
      ]);
      expect(toggles().map((t) => t.getAttribute('aria-pressed'))).toEqual(['false', 'true']);
      expect(tavern.button('Nouveau PNJ')).toBeTruthy();
    });

    it('the master creating in their adventure goes to the form', async () => {
      await open({ creatorId: 'me', players: ['p1'] });
      await answer(cast());
      tavern.button('Nouveau PNJ').click();
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/adventures/a1/characters/new');
    });

    it('toggling_visibility_is_one_call_and_rolls_back_on_error', async () => {
      await open({ creatorId: 'me', players: ['p1'] }, withUsage);
      await answer(cast());

      toggles()[0].click();
      await tavern.render();
      // Optimistic: the toggle already shows the new state while the call is pending.
      expect(toggles()[0].getAttribute('aria-pressed')).toBe('true');
      const request = tavern.backend.expectOne(setVisibility);
      expect(request.request.body).toEqual({ visibility: 'PUBLIC' });
      request.flush({}, { status: 500, statusText: 'Server' });
      await tavern.render();
      expect(toggles()[0].getAttribute('aria-pressed')).toBe('false');
      expect(TestBed.inject(NotifierService).toasts()[0].kind).toBe('error');

      toggles()[1].click();
      await tavern.render();
      const second = tavern.backend.expectOne(setVisibility);
      expect(second.request.body).toEqual({ visibility: 'PRIVATE' });
      second.flush(character({ id: 'n2', npc: true, ownerId: 'me', visibility: 'PRIVATE' }));
      await tavern.render();
      expect(toggles()[1].getAttribute('aria-pressed')).toBe('false');
    });

    it('players_see_public_npcs_read_only', async () => {
      await open({ players: ['me', 'p1'] }, withUsage);
      await answer([
        character({ id: 'c1', name: 'Thorin', ownerId: 'p1' }),
        character({
          id: 'n2',
          name: 'Aubergiste',
          ownerId: 'master',
          npc: true,
          visibility: 'PUBLIC',
        }),
      ]);
      expect(names(section('PNJ'))).toEqual(['Aubergiste']);
      expect(section('PNJ')!.querySelector('.npc')!.textContent).toContain('PNJ');
      expect(section('PNJ')!.textContent).not.toContain('/ 5');
      expect(toggles()).toEqual([]);
      expect(tavern.main().textContent).not.toContain('Rendre actif');
      expect(tavern.button('Créer un personnage')).toBeTruthy();
    });
  });
});
