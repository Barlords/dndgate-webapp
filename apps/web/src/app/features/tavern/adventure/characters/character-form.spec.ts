import { TestBed } from '@angular/core/testing';
import { AdventureDto } from '../../../../core/api/generated/models/adventure-dto';
import { NotifierService } from '../../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../../layout/breadcrumb.service';
import { adventure, setupTavern, TavernTestbed } from '../../tavern.testing';
import {
  character,
  fileOf,
  GIF_BYTES,
  JPEG_BYTES,
  PNG_BYTES,
  stubObjectUrls,
} from './characters.testing';

interface Req {
  method: string;
  url: string;
}

describe('CharacterForm (creation and edition pages)', () => {
  let tavern: TavernTestbed;
  let urls: ReturnType<typeof stubObjectUrls>;

  const getAdventure = (r: Req) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url);
  const post = (r: Req) => r.method === 'POST' && /\/game-characters$/.test(r.url);
  const getOne = (r: Req) => r.method === 'GET' && /\/game-characters\/c1$/.test(r.url);
  const patch = (r: Req) => r.method === 'PATCH' && /\/game-characters\/c1$/.test(r.url);

  async function open(url: string, overrides: Partial<AdventureDto> = {}) {
    tavern = await setupTavern();
    await tavern.go(url);
    tavern.backend.expectOne(getAdventure).flush(adventure({ players: ['me'], ...overrides }));
    await tavern.render();
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
  const valid = {
    name: 'Thorin',
    race: 'Nain',
    martialClass: 'Guerrier',
    personality: 'Têtu.',
    background: 'Roi sous la montagne.',
  };

  async function choose(file: File) {
    const picker = tavern.main().querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(picker, 'files', { value: [file], configurable: true });
    picker.dispatchEvent(new Event('change'));
    await new Promise((resolve) => setTimeout(resolve));
    await tavern.render();
  }
  const alerts = () =>
    [...tavern.main().querySelectorAll('[role="alert"]')].map((a) => a.textContent?.trim());

  beforeEach(() => {
    urls = stubObjectUrls();
  });
  afterEach(() => {
    tavern.backend.verify();
    vi.unstubAllGlobals();
  });

  describe('creation', () => {
    beforeEach(async () => {
      await open('/tavern/adventures/a1/characters/new');
    });

    it('rejects a gif renamed png by its signature', async () => {
      await choose(fileOf(GIF_BYTES, 'hero.png'));
      expect(alerts()).toEqual(['L’image doit être un PNG ou un JPEG.']);
      expect(tavern.main().querySelector('img.preview')).toBeNull();
    });

    it('rejects a picture over 5 MB', async () => {
      await choose(fileOf(PNG_BYTES, 'hero.png', 5 * 1024 * 1024 + 1));
      expect(alerts()).toEqual(['L’image pèse plus de 5 Mo.']);
    });

    it('accepts a png and a jpeg and previews the chosen one', async () => {
      await choose(fileOf(PNG_BYTES, 'a.png'));
      expect(alerts()).toEqual([]);
      expect(tavern.main().querySelector('img.preview')!.getAttribute('src')).toBe('blob:1');
      await choose(fileOf(JPEG_BYTES, 'b.jpg'));
      expect(urls.revoked).toEqual(['blob:1']);
      expect(tavern.main().querySelector('img.preview')!.getAttribute('src')).toBe('blob:2');
    });

    it('a refused file replaces the previous valid choice', async () => {
      await choose(fileOf(PNG_BYTES, 'a.png'));
      await choose(fileOf(GIF_BYTES, 'b.png'));
      expect(tavern.main().querySelector('img.preview')).toBeNull();
      fill(valid);
      tavern.button('Créer le personnage').click();
      tavern.backend.expectNone(post);
    });

    it('requires a picture', async () => {
      fill(valid);
      tavern.button('Créer le personnage').click();
      await tavern.render();
      tavern.backend.expectNone(post);
      expect(alerts()).toEqual(['Choisis une image pour ton personnage.']);
    });

    it('checks the lengths of the fields', async () => {
      fill({ ...valid, name: 'Th', race: ' ', background: 'x'.repeat(80_001) });
      await choose(fileOf(PNG_BYTES, 'a.png'));
      tavern.button('Créer le personnage').click();
      await tavern.render();
      tavern.backend.expectNone(post);
      const text = tavern.main().textContent!;
      expect(text).toContain('Saisis au moins 3 caractères.');
      expect(text).toContain('Ce champ est obligatoire.');
      expect(text).toContain('80000 caractères au maximum.');
    });

    it('counts the characters of the story', async () => {
      fill({ background: 'abcd' });
      await tavern.render();
      expect(tavern.main().textContent).toContain('4 / 80 000');
    });

    it('create sends a multipart body with both parts', async () => {
      fill({ ...valid, name: '  Thorin  ' });
      await choose(fileOf(PNG_BYTES, 'hero.png'));
      tavern.button('Créer le personnage').click();
      const request = tavern.backend.expectOne(post);
      const body = request.request.body as FormData;
      expect(body).toBeInstanceOf(FormData);
      expect((body.get('picturePresentation') as File).name).toBe('hero.png');
      const json = body.get('createGameCharacterRequest') as Blob;
      expect(json.type).toBe('application/json');
      expect(JSON.parse(await json.text())).toEqual({
        ...valid,
        name: 'Thorin',
        adventureId: 'a1',
      });
      request.flush(character({ id: 'c9' }), { status: 201, statusText: 'Created' });
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/adventures/a1/characters/c9');
      expect(TestBed.inject(NotifierService).toasts()[0].message).toBe('Personnage créé.');
      tavern.backend
        .expectOne((r) => r.method === 'GET' && /\/game-characters\/c9$/.test(r.url))
        .flush(character({ id: 'c9' }));
    });

    it('explains the limit of 10 characters on a 409', async () => {
      fill(valid);
      await choose(fileOf(PNG_BYTES, 'hero.png'));
      tavern.button('Créer le personnage').click();
      tavern.backend.expectOne(post).flush({}, { status: 409, statusText: 'Conflict' });
      await tavern.render();
      expect(alerts()).toEqual(['Tu as déjà 10 personnages dans cette aventure.']);
    });

    it('puts the field messages of a 400 on the fields', async () => {
      fill(valid);
      await choose(fileOf(PNG_BYTES, 'hero.png'));
      tavern.button('Créer le personnage').click();
      tavern.backend
        .expectOne(post)
        .flush(
          { errors: [{ field: 'race', message: 'trop courte' }] },
          { status: 400, statusText: 'Bad Request' },
        );
      await tavern.render();
      expect(tavern.main().textContent).toContain('trop courte');
    });

    it('shows any other failure as a toast', async () => {
      fill(valid);
      await choose(fileOf(PNG_BYTES, 'hero.png'));
      tavern.button('Créer le personnage').click();
      tavern.backend.expectOne(post).flush({}, { status: 500, statusText: 'Server' });
      expect(TestBed.inject(NotifierService).toasts()[0].message).toContain(
        'Création du personnage',
      );
    });
  });

  describe('edition', () => {
    async function openEdit(owner = 'me') {
      await open('/tavern/adventures/a1/characters/c1/edit');
      tavern.backend.expectOne(getOne).flush(character({ ownerId: owner }));
      await tavern.render();
    }

    it('starts from the character, has no picture field and names the breadcrumb', async () => {
      await openEdit();
      expect(input('name').value).toBe('Thorin');
      expect(tavern.main().querySelector('input[type="file"]')).toBeNull();
      expect(
        TestBed.inject(BreadcrumbService)
          .crumbs()
          .map((c) => c.label),
      ).toEqual(['Taverne', 'La mine perdue', 'Personnages', 'Thorin', 'Modifier']);
    });

    it('sends the fields and goes back to the character', async () => {
      await openEdit();
      fill({ race: 'Elfe' });
      tavern.button('Enregistrer').click();
      const request = tavern.backend.expectOne(patch);
      expect(request.request.body).toEqual({
        name: 'Thorin',
        race: 'Elfe',
        martialClass: 'Guerrier',
        personality: 'Têtu.',
        background: 'Roi sous la montagne.',
      });
      request.flush(character({ race: 'Elfe', ownerId: 'me' }));
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/adventures/a1/characters/c1');
      tavern.backend.expectOne(getOne).flush(character({ race: 'Elfe', ownerId: 'me' }));
    });

    it('offers to reload on a concurrent change and keeps the typing', async () => {
      await openEdit();
      fill({ race: 'Elfe' });
      tavern.button('Enregistrer').click();
      tavern.backend.expectOne(patch).flush({}, { status: 409, statusText: 'Conflict' });
      const notifier = TestBed.inject(NotifierService);
      expect(notifier.toasts()[0].action?.label).toBe('Recharger');
      expect(input('race').value).toBe('Elfe');
      notifier.act(notifier.toasts()[0]);
      tavern.backend.expectOne(getOne).flush(character({ race: 'Gnome', ownerId: 'me' }));
      await tavern.render();
      expect(input('race').value).toBe('Gnome');
    });

    it('only the owner can edit', async () => {
      await openEdit('someone');
      expect(tavern.main().querySelector('form')).toBeNull();
      expect(tavern.main().textContent).toContain('Seul le propriétaire');
    });

    it('says so when the character is unknown', async () => {
      await open('/tavern/adventures/a1/characters/c1/edit');
      tavern.backend.expectOne(getOne).flush({}, { status: 404, statusText: 'Not Found' });
      await tavern.render();
      expect(alerts()).toEqual(['Ce personnage est introuvable.']);
    });
  });
});
