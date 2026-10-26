import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { GameCharacterDto } from '../../../../core/api/generated/models/game-character-dto';
import { AdventureDto } from '../../../../core/api/generated/models/adventure-dto';
import { NotifierService } from '../../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../../layout/breadcrumb.service';
import { AdventureContext } from '../adventure-context.service';
import { ReserveFullDialog } from '../../characters/reserve-full.dialog';
import { ConfirmService } from '../../../../shared/ui/confirm-dialog/confirm.service';
import { adventure, page, setupTavern, TavernTestbed } from '../../tavern.testing';
import { character, stubObjectUrls } from './characters.testing';

interface Req {
  method: string;
  url: string;
}

describe('CharacterDetailPage', () => {
  let tavern: TavernTestbed;

  /** The context the adventure shell provides to its pages. */
  const shellContext = () =>
    tavern.harness.fixture.debugElement
      .query(By.css('dg-adventure-shell'))
      .injector.get(AdventureContext);

  const getAdventure = (r: Req) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url);
  const getOne = (r: Req) => r.method === 'GET' && /\/game-characters\/c1$/.test(r.url);
  const picture = (r: Req) => /\/game-characters\/c1\/picture$/.test(r.url);
  const remove = (r: Req) => r.method === 'DELETE' && /\/game-characters\/c1$/.test(r.url);

  async function start(overrides: Partial<AdventureDto> = {}) {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/characters/c1');
    tavern.backend.expectOne(getAdventure).flush(adventure({ players: ['me'], ...overrides }));
    await tavern.render();
  }

  async function open(
    owner = 'me',
    overrides: Partial<GameCharacterDto> = {},
    adventureOverrides: Partial<AdventureDto> = {},
  ) {
    await start(adventureOverrides);
    tavern.backend.expectOne(getOne).flush(character({ ownerId: owner, ...overrides }));
    await tavern.render();
    tavern.backend.expectOne(picture).flush(new Blob(['x']));
    tavern.backend
      .match((r) => /\/accounts\/id\//.test(r.url))
      .forEach((r) => r.flush({ id: 'x', pseudo: 'Gimli' }));
    await tavern.render();
  }

  const reserve = (r: Req) =>
    r.method === 'DELETE' && /\/game-characters\/c1\/adventure$/.test(r.url);
  const planFull = {
    type: 'urn:dndgate:plan-limit-reached',
    limit: 'charactersWithoutAdventure',
    max: 3,
    planId: 'adventurer',
    upgradePlanId: 'hero',
    decidedBy: 'account',
  };

  const buttons = () =>
    [...tavern.main().querySelectorAll('button')].map((b) => b.textContent?.trim());

  beforeEach(() => stubObjectUrls());
  afterEach(() => {
    tavern.backend.verify();
    vi.unstubAllGlobals();
  });

  it('shows the portrait and the fields, and names the breadcrumb', async () => {
    await open();
    const text = tavern.main().textContent!;
    for (const expected of [
      'Thorin',
      'Nain',
      'Guerrier',
      'Têtu.',
      'Roi sous la montagne.',
      'Gimli',
    ]) {
      expect(text).toContain(expected);
    }
    expect(tavern.main().querySelector('img')!.getAttribute('src')).toBe('blob:1');
    expect(
      TestBed.inject(BreadcrumbService)
        .crumbs()
        .map((c) => c.label),
    ).toEqual(['Taverne', 'La mine perdue', 'Personnages', 'Thorin']);
  });

  it('the owner can edit and delete', async () => {
    await open('me');
    expect(buttons()).toEqual(['Modifier', 'Supprimer']);
    tavern.button('Modifier').click();
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/adventures/a1/characters/c1/edit');
    tavern.backend.expectOne(getOne).flush(character({ ownerId: 'me' }));
  });

  it('the_master_manages_an_npc_whoever_owns_it', async () => {
    await open('someone', { npc: true, visibility: 'PUBLIC' }, { creatorId: 'me' });
    expect(buttons()).toEqual(['Modifier', 'Supprimer']);
    const typeToConfirm = vi
      .spyOn(TestBed.inject(ConfirmService), 'typeToConfirm')
      .mockReturnValue(of('Thorin'));
    const reload = vi.spyOn(shellContext(), 'reloadEntitlements');
    tavern.button('Supprimer').click();
    expect(typeToConfirm).toHaveBeenCalled();
    tavern.backend.expectOne(remove).flush(null, { status: 204, statusText: 'No Content' });
    await tavern.render();
    expect(reload).toHaveBeenCalled();
    tavern.backend
      .match((r) => r.method === 'GET' && /\/game-characters$/.test(r.url))
      .forEach((r) => r.flush([]));
  });

  it('a_player_cannot_manage_an_npc_even_if_it_is_theirs', async () => {
    await open('me', { npc: true, visibility: 'PUBLIC' });
    expect(buttons()).toEqual([]);
  });

  it('another participant can only read', async () => {
    await open('someone');
    expect(buttons()).toEqual([]);
  });

  it('delete requires the character name', async () => {
    await open();
    const typeToConfirm = vi
      .spyOn(TestBed.inject(ConfirmService), 'typeToConfirm')
      .mockReturnValue(of('Thorin'));
    tavern.button('Supprimer').click();
    expect(typeToConfirm).toHaveBeenCalledWith(
      expect.objectContaining({ expected: 'Thorin', inputType: 'text' }),
    );
    const request = tavern.backend.expectOne(remove);
    expect(request.request.body).toEqual({ confirmation: 'Thorin' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await tavern.render();
    expect(tavern.router.url).toBe('/tavern/adventures/a1/characters');
    expect(TestBed.inject(NotifierService).toasts()[0].message).toBe('Personnage supprimé.');
    tavern.backend
      .expectOne((r) => r.method === 'GET' && /\/game-characters$/.test(r.url))
      .flush([]);
  });

  it('does nothing when the deletion is cancelled', async () => {
    await open();
    vi.spyOn(TestBed.inject(ConfirmService), 'typeToConfirm').mockReturnValue(of(null));
    tavern.button('Supprimer').click();
    tavern.backend.expectNone(remove);
  });

  it('shows a failed deletion as a toast', async () => {
    await open();
    vi.spyOn(TestBed.inject(ConfirmService), 'typeToConfirm').mockReturnValue(of('Thorin'));
    tavern.button('Supprimer').click();
    tavern.backend.expectOne(remove).flush({}, { status: 500, statusText: 'Server' });
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain(
      'Suppression du personnage',
    );
  });

  it('says so when the character is unknown', async () => {
    await start();
    tavern.backend.expectOne(getOne).flush({}, { status: 404, statusText: 'Not Found' });
    await tavern.render();
    expect(tavern.main().querySelector('[role="alert"]')!.textContent).toContain('introuvable');
  });

  it('offers to retry when the character cannot be loaded', async () => {
    await start();
    tavern.backend.expectOne(getOne).flush({}, { status: 500, statusText: 'Server' });
    await tavern.render();
    tavern.button('Réessayer').click();
    tavern.backend.expectOne(getOne).flush({}, { status: 500, statusText: 'Server' });
  });

  describe('reserve', () => {
    const inAdventure = { adventureId: 'a1' };
    const noContent = { status: 204, statusText: 'No Content' };

    it('moving_to_the_reserve_frees_the_place', async () => {
      await open('me', inAdventure);
      expect(buttons()).toEqual(['Modifier', 'Mettre en réserve', 'Supprimer']);
      tavern.button('Mettre en réserve').click();
      tavern.backend.expectOne(reserve).flush(character({ ownerId: 'me', adventureId: null }));
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/adventures/a1/characters');
      expect(TestBed.inject(NotifierService).toasts()[0].message).toBe(
        'Personnage mis en réserve.',
      );
      tavern.backend
        .expectOne((r) => r.method === 'GET' && /\/game-characters$/.test(r.url))
        .flush([]);
    });

    it('a_full_reserve_offers_cancel_or_delete', async () => {
      await open('me', inAdventure);
      const ask = vi.spyOn(TestBed.inject(ReserveFullDialog), 'ask').mockReturnValue(of('cancel'));
      const typeToConfirm = vi.spyOn(TestBed.inject(ConfirmService), 'typeToConfirm');
      tavern.button('Mettre en réserve').click();
      tavern.backend.expectOne(reserve).flush(planFull, { status: 409, statusText: 'Conflict' });
      await tavern.render();
      expect(ask).toHaveBeenCalledWith('Thorin');
      expect(typeToConfirm).not.toHaveBeenCalled();
      tavern.backend.expectNone(remove);
      expect(TestBed.inject(NotifierService).toasts()).toEqual([]);
      expect(tavern.router.url).toBe('/tavern/adventures/a1/characters/c1');
    });

    it('deleting_from_the_full_reserve_dialog_requires_the_name', async () => {
      await open('me', inAdventure);
      vi.spyOn(TestBed.inject(ReserveFullDialog), 'ask').mockReturnValue(of('delete'));
      const typeToConfirm = vi
        .spyOn(TestBed.inject(ConfirmService), 'typeToConfirm')
        .mockReturnValue(of('Thorin'));
      tavern.button('Mettre en réserve').click();
      tavern.backend.expectOne(reserve).flush(planFull, { status: 409, statusText: 'Conflict' });
      expect(typeToConfirm).toHaveBeenCalledWith(
        expect.objectContaining({ expected: 'Thorin', inputType: 'text' }),
      );
      const request = tavern.backend.expectOne(remove);
      expect(request.request.body).toEqual({ confirmation: 'Thorin' });
      request.flush(null, noContent);
      await tavern.render();
      expect(tavern.router.url).toBe('/tavern/adventures/a1/characters');
      tavern.backend
        .expectOne((r) => r.method === 'GET' && /\/game-characters$/.test(r.url))
        .flush([]);
    });

    it('does not delete when the name is not retyped after the full reserve dialog', async () => {
      await open('me', inAdventure);
      vi.spyOn(TestBed.inject(ReserveFullDialog), 'ask').mockReturnValue(of('delete'));
      vi.spyOn(TestBed.inject(ConfirmService), 'typeToConfirm').mockReturnValue(of(null));
      tavern.button('Mettre en réserve').click();
      tavern.backend.expectOne(reserve).flush(planFull, { status: 409, statusText: 'Conflict' });
      tavern.backend.expectNone(remove);
    });

    it('any other refusal of the reserve is a toast, without the dialog', async () => {
      await open('me', inAdventure);
      const ask = vi.spyOn(TestBed.inject(ReserveFullDialog), 'ask');
      tavern.button('Mettre en réserve').click();
      tavern.backend.expectOne(reserve).flush({}, { status: 500, statusText: 'Server' });
      expect(ask).not.toHaveBeenCalled();
      expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('réserve');
    });

    it('an NPC has no reserve button', async () => {
      await open('me', { adventureId: 'a1', npc: true });
      expect(buttons()).not.toContain('Mettre en réserve');
    });
  });

  describe('send to an adventure', () => {
    const asPlayer = (r: Req) => r.method === 'GET' && /\/adventures\/as-player$/.test(r.url);
    const move = (r: Req) => r.method === 'PUT' && /\/game-characters\/c1\/adventure$/.test(r.url);

    async function openReserve(overrides: Partial<GameCharacterDto> = {}) {
      tavern = await setupTavern();
      await tavern.go('/tavern/characters/c1');
      tavern.backend.expectOne(getOne).flush(character({ ownerId: 'me', ...overrides }));
      await tavern.render();
      tavern.backend.expectOne(picture).flush(new Blob(['x']));
      tavern.backend.match((r) => /\/accounts\/id\//.test(r.url)).forEach((r) => r.flush({}));
      await tavern.render();
    }

    async function chooseAdventures() {
      tavern.button('Envoyer en aventure').click();
      tavern.backend
        .expectOne(asPlayer)
        .flush(
          page([
            adventure({ id: 'a1', name: 'La mine perdue', status: 'ONGOING' }),
            adventure({ id: 'a2', name: 'Terminée', status: 'FINISH' }),
            adventure({ id: 'a3', name: 'Le donjon', status: 'NOT_STARTED' }),
            adventure({ id: 'a4', name: 'Abandonnée', status: 'ABORT' }),
          ]),
        );
      await tavern.render();
    }

    const select = () => tavern.main().querySelector('select')!;

    it('a_reserve_character_is_sent_to_an_adventure_of_the_reader', async () => {
      await openReserve();
      await chooseAdventures();
      expect([...select().options].map((o) => o.textContent?.trim())).toEqual([
        'La mine perdue',
        'Le donjon',
      ]);
      select().value = 'a3';
      select().dispatchEvent(new Event('change'));
      await tavern.render();
      tavern.button('Envoyer').click();
      const request = tavern.backend.expectOne(move);
      expect(request.request.body).toEqual({ adventureId: 'a3' });
      request.flush(character({ ownerId: 'me', adventureId: 'a3' }));
      await tavern.render();
      expect(TestBed.inject(NotifierService).toasts()[0].message).toBe(
        'Personnage envoyé en aventure.',
      );
      expect(tavern.main().querySelector('select')).toBeNull();
      expect(tavern.main().textContent).toContain('Voir son aventure');
      expect(buttons()).not.toContain('Envoyer en aventure');
    });

    it('says so when the reader plays in no adventure to come or ongoing', async () => {
      await openReserve();
      tavern.button('Envoyer en aventure').click();
      tavern.backend.expectOne(asPlayer).flush(page([adventure({ id: 'a2', status: 'FINISH' })]));
      await tavern.render();
      expect(tavern.main().querySelector('select')).toBeNull();
      expect(tavern.main().querySelector('[role="status"]')!.textContent).toContain(
        'Tu ne joues dans aucune aventure',
      );
    });

    it('a plan refusal of the move goes through the notifier', async () => {
      await openReserve();
      await chooseAdventures();
      tavern.button('Envoyer').click();
      tavern.backend.expectOne(move).flush(
        {
          type: 'urn:dndgate:plan-limit-reached',
          limit: 'charactersPerPlayerAndAdventure',
          max: 1,
          planId: 'adventurer',
          upgradePlanId: 'hero',
          decidedBy: 'master',
        },
        { status: 409, statusText: 'Conflict' },
      );
      await tavern.render();
      const toast = TestBed.inject(NotifierService).toasts()[0];
      expect(toast.kind).toBe('error');
      expect(toast.message).not.toBe('');
      expect(select()).not.toBeNull();
    });

    it('is not offered for an NPC', async () => {
      await openReserve({ npc: true });
      expect(buttons()).not.toContain('Envoyer en aventure');
    });
  });
});
