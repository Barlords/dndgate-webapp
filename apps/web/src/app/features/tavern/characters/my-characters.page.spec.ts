import { TestBed } from '@angular/core/testing';
import { GameCharacterDto } from '../../../core/api/generated/models/game-character-dto';
import { NotifierService } from '../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import {
  character,
  fileOf,
  PNG_BYTES,
  stubObjectUrls,
} from '../adventure/characters/characters.testing';
import { setupTavern, TavernTestbed } from '../tavern.testing';

interface Req {
  method: string;
  url: string;
}

describe('MyCharactersPage and the characters outside any adventure', () => {
  let tavern: TavernTestbed;

  const mine = (r: Req) => r.method === 'GET' && /\/game-characters\/mine$/.test(r.url);
  const pictures = (r: Req) => /\/game-characters\/[^/]+\/picture$/.test(r.url);
  const accounts = (r: Req) => /\/accounts\/id\//.test(r.url);
  const post = (r: Req) => r.method === 'POST' && /\/game-characters$/.test(r.url);
  const getOne = (r: Req) => r.method === 'GET' && /\/game-characters\/c1$/.test(r.url);
  const patch = (r: Req) => r.method === 'PATCH' && /\/game-characters\/c1$/.test(r.url);

  const subscription = (r: Req) =>
    r.method === 'GET' && /\/accounts\/me\/subscription$/.test(r.url);
  const plans = (r: Req) => r.method === 'GET' && /\/plans$/.test(r.url);
  const CATALOG = {
    currency: 'EUR',
    defaultPlan: 'adventurer',
    plans: [
      { id: 'adventurer', features: { characterPictures: false } },
      { id: 'hero', features: { characterPictures: true } },
    ],
  };

  /** The creation page looks at the reader's own plan to know whether a picture may be sent. */
  async function answerPlan(planId = 'hero') {
    tavern.backend.expectOne(subscription).flush({ planId, status: 'active' });
    tavern.backend.expectOne(plans).flush(CATALOG);
    await tavern.render();
  }

  async function settle() {
    await tavern.render();
    tavern.backend.match(pictures).forEach((r) => r.flush(new Blob(['x'])));
    tavern.backend.match(accounts).forEach((r) => r.flush({ id: 'me', pseudo: 'Me' }));
    await tavern.render();
  }

  async function open(characters: GameCharacterDto[]) {
    tavern = await setupTavern();
    await tavern.go('/tavern/characters');
    tavern.backend.expectOne(mine).flush(characters);
    await settle();
  }

  const input = (name: string) =>
    tavern
      .main()
      .querySelector<HTMLInputElement | HTMLTextAreaElement>(`[formControlName="${name}"]`)!;
  function fill(values: Record<string, string>) {
    for (const [name, value] of Object.entries(values)) {
      input(name).value = value;
      input(name).dispatchEvent(new Event('input'));
    }
  }

  beforeEach(() => stubObjectUrls());
  afterEach(() => {
    tavern.backend.verify();
    vi.unstubAllGlobals();
  });

  describe('list', () => {
    const both = [
      character({ id: 'c2', name: 'Thorin', ownerId: 'me', adventureId: 'a1' }),
      character({ id: 'c1', name: 'Elara', ownerId: 'me', race: 'Elfe' }),
    ];

    it('shows every character of the reader as portrait cards, sorted by name', async () => {
      await open(both);
      const cards = [...tavern.main().querySelectorAll('.card')];
      expect(cards.map((c) => c.querySelector('.name')!.textContent?.trim())).toEqual([
        'Elara',
        'Thorin',
      ]);
      expect(cards[0].textContent).toContain('Hors aventure');
      expect(cards[1].textContent).toContain('Dans une aventure');
      expect(cards[0].getAttribute('href')).toBe('/tavern/characters/c1');
    });

    it('shows them as a table, with a way to their adventure', async () => {
      await open(both);
      tavern.main().querySelector<HTMLButtonElement>('[aria-label="Afficher en tableau"]')!.click();
      await tavern.render();
      const rows = [...tavern.main().querySelectorAll('tbody tr')].map((tr) =>
        [...tr.querySelectorAll('td')].map((td) => td.textContent?.trim()),
      );
      expect(rows).toEqual([
        ['Elara', 'Elfe', 'Guerrier', 'Hors aventure'],
        ['Thorin', 'Nain', 'Guerrier', 'Voir l’aventure'],
      ]);
      const links = [...tavern.main().querySelectorAll('tbody a')].map((a) =>
        a.getAttribute('href'),
      );
      expect(links).toEqual([
        '/tavern/characters/c1',
        '/tavern/characters/c2',
        '/tavern/adventures/a1',
      ]);
    });

    it('says so when there is none', async () => {
      await open([]);
      expect(tavern.main().textContent).toContain('Tu n’as pas encore de personnage.');
    });

    it('offers a retry on a failure', async () => {
      tavern = await setupTavern();
      await tavern.go('/tavern/characters');
      tavern.backend.expectOne(mine).flush({}, { status: 500, statusText: 'Error' });
      await tavern.render();
      tavern.button('Réessayer').click();
      tavern.backend.expectOne(mine).flush([]);
      await tavern.render();
      expect(tavern.main().textContent).toContain('Tu n’as pas encore de personnage.');
    });

    it('opens the creation from its button', async () => {
      await open([]);
      tavern.main().querySelector<HTMLButtonElement>('[aria-label="Créer un personnage"]')!.click();
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/characters/new');
      await answerPlan();
    });
  });

  describe('creation outside any adventure', () => {
    beforeEach(async () => {
      tavern = await setupTavern();
      await tavern.go('/tavern/characters/new');
    });

    it('keeps the picture field when the own plan cannot be known', async () => {
      tavern.backend.expectOne(subscription).flush({}, { status: 500, statusText: 'Error' });
      tavern.backend.expectOne(plans).flush(CATALOG);
      await tavern.render();
      expect(tavern.main().querySelector('input[type="file"]')).not.toBeNull();
      expect(tavern.main().querySelector('dg-plan-hint')).toBeNull();
    });

    it('the_picture_field_is_replaced_by_the_hint_without_the_feature', async () => {
      await answerPlan('adventurer');
      expect(tavern.main().querySelector('input[type="file"]')).toBeNull();
      expect(tavern.main().querySelector('dg-plan-hint')?.textContent).toContain(
        'Inclus à partir de l’offre Héros.',
      );
    });

    async function submit() {
      await answerPlan();
      fill({
        name: 'Elara',
        race: 'Elfe',
        martialClass: 'Rôdeuse',
        personality: 'Discrète.',
        background: 'Née dans la forêt.',
      });
      const picker = tavern.main().querySelector<HTMLInputElement>('input[type="file"]')!;
      Object.defineProperty(picker, 'files', {
        value: [fileOf(PNG_BYTES, 'elara.png')],
        configurable: true,
      });
      picker.dispatchEvent(new Event('change'));
      await new Promise((resolve) => setTimeout(resolve));
      await tavern.render();
      tavern.button('Créer le personnage').click();
      return tavern.backend.expectOne(post);
    }

    it('draws the form on the parchment, like the other forms', async () => {
      await answerPlan();
      expect(tavern.main().querySelector('dg-parchment dg-character-form')).not.toBeNull();
    });

    it('sends no adventure and opens the new character among mine', async () => {
      const request = await submit();
      const json = (request.request.body as FormData).get('createGameCharacterRequest') as Blob;
      expect(JSON.parse(await json.text())).not.toHaveProperty('adventureId');
      request.flush(character({ id: 'c9', ownerId: 'me' }), { status: 201, statusText: 'Created' });
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/characters/c9');
      expect(TestBed.inject(NotifierService).toasts()[0].message).toBe('Personnage créé.');
      tavern.backend
        .expectOne((r) => r.method === 'GET' && /\/game-characters\/c9$/.test(r.url))
        .flush(character({ id: 'c9', ownerId: 'me' }));
      await settle();
    });

    it('explains the limit of the characters outside any adventure on a 409', async () => {
      const request = await submit();
      request.flush({}, { status: 409, statusText: 'Conflict' });
      await tavern.render();
      expect(tavern.main().textContent).toContain('Tu as déjà 50 personnages hors aventure.');
    });
  });

  describe('a character of mine', () => {
    async function openDetail(overrides: Partial<GameCharacterDto> = {}) {
      tavern = await setupTavern();
      await tavern.go('/tavern/characters/c1');
      tavern.backend.expectOne(getOne).flush(character({ ownerId: 'me', ...overrides }));
      await settle();
    }

    it('reads without an adventure around and names the breadcrumb', async () => {
      await openDetail();
      expect(tavern.main().textContent).toContain('Hors aventure');
      expect(tavern.main().querySelector('dg-parchment dg-character-portrait')).not.toBeNull();
      expect(tavern.main().querySelector('a[href="/tavern/characters"]')).not.toBeNull();
      expect(
        TestBed.inject(BreadcrumbService)
          .crumbs()
          .map((c) => c.label),
      ).toEqual(['Taverne', 'Mes personnages', 'Thorin']);
    });

    it('leads to its adventure when it has one', async () => {
      await openDetail({ adventureId: 'a1' });
      expect(tavern.main().querySelector('a[href="/tavern/adventures/a1"]')?.textContent).toContain(
        'Voir son aventure',
      );
    });

    it('is edited and comes back among mine', async () => {
      await openDetail();
      tavern.button('Modifier').click();
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/characters/c1/edit');
      tavern.backend.expectOne(getOne).flush(character({ ownerId: 'me' }));
      await tavern.render();
      fill({ race: 'Elfe' });
      tavern.button('Enregistrer').click();
      tavern.backend.expectOne(patch).flush(character({ ownerId: 'me', race: 'Elfe' }));
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/characters/c1');
      tavern.backend.expectOne(getOne).flush(character({ ownerId: 'me', race: 'Elfe' }));
      await settle();
    });
  });
});
