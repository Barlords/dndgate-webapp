import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { adventure, setupTavern, TavernTestbed } from '../tavern.testing';
import { adventureNote, notesPage, Req, today } from './notes.testing';

describe('GmNotesPage', () => {
  let tavern: TavernTestbed;

  const getAdventure = (r: Req) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url);
  const list = (r: Req) =>
    r.method === 'GET' && /\/adventure-notes$/.test(r.url) && r.params.get('adventureId') === 'a1';
  const accounts = (r: Req) => /\/accounts\/id\//.test(r.url);

  async function open(overrides: Partial<AdventureDto> = {}) {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/gm-notes');
    tavern.backend
      .expectOne(getAdventure)
      .flush(adventure({ creatorId: 'me', players: ['p1'], ...overrides }));
    await tavern.render();
  }

  async function answer(notes = [adventureNote()]) {
    tavern.backend.expectOne(list).flush(notesPage(notes));
    tavern.backend.match(accounts).forEach((r) => r.flush({}));
    await tavern.render();
  }

  const items = () => [...tavern.main().querySelectorAll('.note')];

  afterEach(() => tavern.backend.verify());

  it('gm notes marks private ones', async () => {
    await open();
    await answer([
      adventureNote({ id: 'g2', content: 'Secret.', noteVisibility: 'PRIVATE' }),
      adventureNote({ id: 'g1', content: 'Connu.' }),
    ]);
    expect(items().map((n) => !!n.querySelector('.badge'))).toEqual([true, false]);
    expect(items()[0].querySelector('.badge')!.textContent).toContain('Privée');
  });

  it('the master creates a private note', async () => {
    await open();
    await answer([]);
    tavern.button('Ajouter une note').click();
    await tavern.render();
    const textarea = tavern.main().querySelector('textarea')!;
    textarea.value = 'Piège au sous-sol.';
    textarea.dispatchEvent(new Event('input'));
    const select = tavern.main().querySelector('select')!;
    select.value = 'PRIVATE';
    select.dispatchEvent(new Event('change'));
    await tavern.render();
    tavern.button('Inscrire').click();
    const post = tavern.backend.expectOne(
      (r) => r.method === 'POST' && /\/adventure-notes$/.test(r.url),
    );
    expect(post.request.body).toEqual({
      adventureId: 'a1',
      content: 'Piège au sous-sol.',
      date: today(),
      visibility: 'PRIVATE',
    });
    post.flush(adventureNote({ noteVisibility: 'PRIVATE' }));
    await tavern.render();
    tavern.backend.expectOne(list).flush(notesPage([adventureNote()]));
    await tavern.render();
    expect(items().length).toBe(1);
  });

  it('players cannot create gm notes', async () => {
    await open({ creatorId: 'master', players: ['me'] });
    await answer([adventureNote()]);
    expect(tavern.main().textContent).toContain('Le roi ment.');
    expect(tavern.button('Ajouter une note')).toBeUndefined();
    expect(tavern.button('Modifier')).toBeUndefined();
    expect(tavern.button('Supprimer')).toBeUndefined();
  });

  it('the master edits visibility', async () => {
    await open();
    await answer();
    tavern.button('Modifier').click();
    await tavern.render();
    const select = tavern.main().querySelector('select')!;
    expect(select.value).toBe('PUBLIC');
    select.value = 'PRIVATE';
    select.dispatchEvent(new Event('change'));
    tavern.button('Enregistrer').click();
    const patch = tavern.backend.expectOne(
      (r) => r.method === 'PATCH' && /\/adventure-notes\/g1$/.test(r.url),
    );
    expect(patch.request.body).toEqual({
      content: 'Le roi ment.',
      date: '2026-03-14',
      visibility: 'PRIVATE',
    });
    patch.flush(adventureNote({ noteVisibility: 'PRIVATE' }));
    await tavern.render();
    tavern.backend.expectOne(list).flush(notesPage([adventureNote({ noteVisibility: 'PRIVATE' })]));
    await tavern.render();
    expect(items()[0].querySelector('.badge')).not.toBeNull();
  });

  it('deleting a gm note sends confirm true', async () => {
    await open();
    await answer();
    vi.spyOn(TestBed.inject(ConfirmService), 'confirm').mockReturnValue(of(true));
    tavern.button('Supprimer').click();
    const del = tavern.backend.expectOne(
      (r) => r.method === 'DELETE' && /\/adventure-notes\/g1$/.test(r.url),
    );
    expect(del.request.body).toEqual({ confirm: true });
    del.flush(null, { status: 204, statusText: 'No Content' });
    await tavern.render();
    tavern.backend.expectOne(list).flush(notesPage([]));
    await tavern.render();
    expect(tavern.main().textContent).toContain('Aucune note du MJ');
  });

  it('asks nothing of the API for someone who is not a participant', async () => {
    await open({ creatorId: 'master', players: ['p1'] });
    tavern.backend.expectNone(list);
    tavern.backend.match(accounts).forEach((r) => r.flush({}));
    expect(tavern.router.url).toBe('/tavern/adventures/a1/overview');
  });
});
