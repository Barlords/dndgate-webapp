import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MagicItemDto } from '../../../core/api/generated/models/magic-item-dto';
import { NotifierService } from '../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { setupTavern, TavernTestbed } from '../tavern.testing';

interface Req {
  method: string;
  url: string;
}

function magicItem(overrides: Partial<MagicItemDto> = {}): MagicItemDto {
  return {
    id: 'm1',
    name: 'Anneau de protection',
    type: 'RING',
    rarity: 'RARE',
    requiresAttunement: true,
    description: '+1 à la CA et aux jets de sauvegarde.',
    ...overrides,
  };
}

describe('My magic items', () => {
  let tavern: TavernTestbed;

  const mine = (r: Req) => r.method === 'GET' && /\/magic-items\/mine$/.test(r.url);
  const post = (r: Req) => r.method === 'POST' && /\/magic-items$/.test(r.url);
  const getOne = (r: Req) => r.method === 'GET' && /\/magic-items\/m1$/.test(r.url);
  const patch = (r: Req) => r.method === 'PATCH' && /\/magic-items\/m1$/.test(r.url);
  const remove = (r: Req) => r.method === 'DELETE' && /\/magic-items\/m1$/.test(r.url);

  const toasts = () => TestBed.inject(NotifierService).toasts();
  const field = <T extends HTMLElement>(name: string) =>
    tavern.main().querySelector<T>(`[formControlName="${name}"]`)!;
  function fill(values: Record<string, string>) {
    for (const [name, value] of Object.entries(values)) {
      const control = field<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(name);
      control.value = value;
      control.dispatchEvent(new Event(control instanceof HTMLSelectElement ? 'change' : 'input'));
    }
  }

  afterEach(() => tavern.backend.verify());

  describe('list', () => {
    async function open(items: MagicItemDto[]) {
      tavern = await setupTavern();
      await tavern.go('/tavern/magic-items');
      tavern.backend.expectOne(mine).flush(items);
      await tavern.render();
    }

    const items = [
      magicItem(),
      magicItem({
        id: 'm2',
        name: 'Potion de soins',
        type: 'POTION',
        rarity: 'COMMON',
        requiresAttunement: false,
        description: 'x'.repeat(300),
      }),
    ];

    it('shows the items as cards with their rarity, type and attunement', async () => {
      await open(items);
      const cards = [...tavern.main().querySelectorAll('.item')];
      expect(cards.map((c) => c.querySelector('.item__name')!.textContent?.trim())).toEqual([
        'Anneau de protection',
        'Potion de soins',
      ]);
      expect(cards[0].textContent).toContain('Rare');
      expect(cards[0].textContent).toContain('Anneau');
      expect(cards[0].querySelector('.seal--attunement')).not.toBeNull();
      expect(cards[1].querySelector('.seal--attunement')).toBeNull();
      expect(cards[1].querySelector('.item__excerpt')!.textContent!.length).toBeLessThan(160);
      expect(cards[0].querySelector('a')!.getAttribute('href')).toBe('/tavern/magic-items/m1');
    });

    it('shows them as a table once chosen', async () => {
      await open(items);
      tavern.main().querySelector<HTMLButtonElement>('[aria-label="Afficher en tableau"]')!.click();
      await tavern.render();
      const rows = [...tavern.main().querySelectorAll('tbody tr')].map((tr) =>
        [...tr.querySelectorAll('td')].map((td) => td.textContent?.trim()),
      );
      expect(rows).toEqual([
        ['Anneau de protection', 'Anneau', 'Rare', 'Requise'],
        ['Potion de soins', 'Potion', 'Commun', '—'],
      ]);
    });

    it('says so when there is none, and opens the creation from its button', async () => {
      await open([]);
      expect(tavern.main().textContent).toContain('Tu n’as pas encore d’objet magique.');
      tavern
        .main()
        .querySelector<HTMLButtonElement>('[aria-label="Créer un objet magique"]')!
        .click();
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/magic-items/new');
    });

    it('offers a retry when the list cannot load', async () => {
      tavern = await setupTavern();
      await tavern.go('/tavern/magic-items');
      tavern.backend.expectOne(mine).flush({}, { status: 500, statusText: 'Error' });
      await tavern.render();
      tavern.button('Réessayer').click();
      tavern.backend.expectOne(mine).flush([magicItem()]);
      await tavern.render();
      expect(tavern.main().querySelectorAll('.item')).toHaveLength(1);
    });
  });

  describe('creation', () => {
    beforeEach(async () => {
      tavern = await setupTavern();
      await tavern.go('/tavern/magic-items/new');
    });

    it('sends the trimmed fields and goes back to the list', async () => {
      fill({
        name: '  Baguette de feu  ',
        type: 'WAND',
        rarity: 'VERY_RARE',
        description: ' Boum. ',
      });
      field<HTMLInputElement>('requiresAttunement').click();
      tavern.button('Créer l’objet').click();
      const request = tavern.backend.expectOne(post);
      expect(request.request.body).toEqual({
        name: 'Baguette de feu',
        type: 'WAND',
        rarity: 'VERY_RARE',
        requiresAttunement: true,
        description: 'Boum.',
      });
      request.flush(magicItem(), { status: 201, statusText: 'Created' });
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/magic-items');
      expect(toasts()[0].message).toBe('Objet magique créé.');
      tavern.backend.expectOne(mine).flush([magicItem()]);
    });

    it('refuses a blank name or description without calling the API', async () => {
      fill({ name: '   ' });
      tavern.button('Créer l’objet').click();
      await tavern.render();
      expect(tavern.main().textContent).toContain('Ce champ est obligatoire.');
      tavern.backend.expectNone(post);
    });

    it('shows the plan refusal on a 409 and puts the field messages of a 400 on the fields', async () => {
      fill({ name: 'Cape', description: 'Elle flotte.' });
      tavern.button('Créer l’objet').click();
      tavern.backend.expectOne(post).flush(
        {
          type: 'urn:dndgate:plan-limit-reached',
          limit: 'magicItems',
          max: 10,
          planId: 'adventurer',
          upgradePlanId: 'hero',
          decidedBy: 'account',
        },
        { status: 409, statusText: 'Conflict' },
      );
      await tavern.render();
      expect(toasts()[0].message).toBe(
        'Création de l’objet magique : Limite atteinte : Objets magiques (10 au maximum). L’offre Héros le permet.',
      );
      expect(toasts()[0].action?.label).toBe('Voir les offres');

      tavern.button('Créer l’objet').click();
      tavern.backend
        .expectOne(post)
        .flush(
          { status: 400, errors: [{ field: 'name', message: 'trop long' }] },
          { status: 400, statusText: 'Bad Request' },
        );
      await tavern.render();
      expect(tavern.main().textContent).toContain('trop long');
    });
  });

  describe('edition', () => {
    async function openEdit(item = magicItem()) {
      tavern = await setupTavern();
      await tavern.go('/tavern/magic-items/m1');
      tavern.backend.expectOne(getOne).flush(item);
      await tavern.render();
    }

    it('starts from the item and names the breadcrumb', async () => {
      await openEdit();
      expect(field<HTMLInputElement>('name').value).toBe('Anneau de protection');
      expect(field<HTMLSelectElement>('rarity').value).toBe('RARE');
      expect(field<HTMLInputElement>('requiresAttunement').checked).toBe(true);
      expect(
        TestBed.inject(BreadcrumbService)
          .crumbs()
          .map((c) => c.label),
      ).toEqual(['Taverne', 'Mes objets magiques', 'Anneau de protection']);
    });

    it('sends the changes and goes back to the list', async () => {
      await openEdit();
      fill({ rarity: 'LEGENDARY' });
      tavern.button('Enregistrer').click();
      const request = tavern.backend.expectOne(patch);
      expect(request.request.body).toEqual({
        name: 'Anneau de protection',
        type: 'RING',
        rarity: 'LEGENDARY',
        requiresAttunement: true,
        description: '+1 à la CA et aux jets de sauvegarde.',
      });
      request.flush(magicItem({ rarity: 'LEGENDARY' }));
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/magic-items');
      tavern.backend.expectOne(mine).flush([]);
    });

    it('offers to reload on a concurrent change', async () => {
      await openEdit();
      tavern.button('Enregistrer').click();
      tavern.backend.expectOne(patch).flush({}, { status: 409, statusText: 'Conflict' });
      await tavern.render();
      toasts()[0].action!.run();
      tavern.backend.expectOne(getOne).flush(magicItem({ name: 'Anneau renommé' }));
      await tavern.render();
      expect(field<HTMLInputElement>('name').value).toBe('Anneau renommé');
    });

    it('is deleted once its name is retyped', async () => {
      await openEdit();
      const typeToConfirm = vi
        .spyOn(TestBed.inject(ConfirmService), 'typeToConfirm')
        .mockReturnValue(of('Anneau de protection'));
      tavern.button('Supprimer').click();
      expect(typeToConfirm).toHaveBeenCalledWith(
        expect.objectContaining({ expected: 'Anneau de protection' }),
      );
      const request = tavern.backend.expectOne(remove);
      expect(request.request.body).toEqual({ confirmation: 'Anneau de protection' });
      request.flush(null, { status: 204, statusText: 'No Content' });
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/magic-items');
      expect(toasts()[0].message).toBe('Objet magique supprimé.');
      tavern.backend.expectOne(mine).flush([]);
    });

    it('deletes nothing when the confirmation is cancelled', async () => {
      await openEdit();
      vi.spyOn(TestBed.inject(ConfirmService), 'typeToConfirm').mockReturnValue(of(null));
      tavern.button('Supprimer').click();
      tavern.backend.expectNone(remove);
    });

    it('says so when the item is unknown', async () => {
      tavern = await setupTavern();
      await tavern.go('/tavern/magic-items/m1');
      tavern.backend.expectOne(getOne).flush({}, { status: 404, statusText: 'Not Found' });
      await tavern.render();
      expect(tavern.main().textContent).toContain('Cet objet magique est introuvable.');
    });
  });
});
