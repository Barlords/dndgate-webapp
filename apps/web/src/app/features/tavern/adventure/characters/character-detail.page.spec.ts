import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdventureDto } from '../../../../core/api/generated/models/adventure-dto';
import { NotifierService } from '../../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../../layout/breadcrumb.service';
import { ConfirmService } from '../../../../shared/ui/confirm-dialog/confirm.service';
import { adventure, setupTavern, TavernTestbed } from '../../tavern.testing';
import { character, stubObjectUrls } from './characters.testing';

interface Req {
  method: string;
  url: string;
}

describe('CharacterDetailPage', () => {
  let tavern: TavernTestbed;

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

  async function open(owner = 'me') {
    await start();
    tavern.backend.expectOne(getOne).flush(character({ ownerId: owner }));
    await tavern.render();
    tavern.backend.expectOne(picture).flush(new Blob(['x']));
    tavern.backend
      .match((r) => /\/accounts\/id\//.test(r.url))
      .forEach((r) => r.flush({ id: 'x', pseudo: 'Gimli' }));
    await tavern.render();
  }

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
});
