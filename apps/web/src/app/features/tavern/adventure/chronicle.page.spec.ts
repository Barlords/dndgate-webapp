import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { NotifierService } from '../../../core/errors/notifier.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { adventure, setupTavern, TavernTestbed } from '../tavern.testing';
import { historicalNote, notesPage, Req, today } from './notes.testing';

describe('ChroniclePage', () => {
  let tavern: TavernTestbed;

  const getAdventure = (r: Req) => r.method === 'GET' && /\/adventures\/a1$/.test(r.url);
  const list = (r: Req) =>
    r.method === 'GET' && /\/historical-notes$/.test(r.url) && r.params.get('adventureId') === 'a1';
  const accounts = (r: Req) => /\/accounts\/id\//.test(r.url);
  const noContent = { status: 204, statusText: 'No Content' };
  const conflict = { status: 409, statusText: 'Conflict' };

  async function open(overrides: Partial<AdventureDto> = {}) {
    tavern = await setupTavern();
    await tavern.go('/tavern/adventures/a1/chronicle');
    tavern.backend
      .expectOne(getAdventure)
      .flush(adventure({ players: ['me', 'p1'], ...overrides }));
    await tavern.render();
  }

  async function answer(notes = [historicalNote()], totalPages = 1, number = 0) {
    tavern.backend.expectOne(list).flush(notesPage(notes, totalPages, number));
    tavern.backend.match(accounts).forEach((r) => r.flush({}));
    await tavern.render();
  }

  const items = () => [...tavern.main().querySelectorAll('.note')];
  const contents = () => items().map((n) => n.querySelector('.content')!.textContent);
  const field = <T extends HTMLElement>(selector: string) =>
    tavern.main().querySelector<T>(selector)!;
  async function type(selector: string, value: string) {
    const el = field<HTMLInputElement>(selector);
    el.value = value;
    el.dispatchEvent(new Event('input'));
    await tavern.render();
  }

  afterEach(() => tavern.backend.verify());

  it('chronicle lists newest first and paginates', async () => {
    await open();
    await answer(
      [
        historicalNote({ id: 'n2', content: 'Plus récent.', date: '2026-05-02' }),
        historicalNote({ id: 'n1', content: 'Plus ancien.\nDeuxième ligne.', date: '2026-03-14' }),
      ],
      2,
    );
    expect(contents()).toEqual(['Plus récent.', 'Plus ancien.\nDeuxième ligne.']);
    expect(items()[0].querySelector('time')!.getAttribute('datetime')).toBe('2026-05-02');
    expect(items()[0].querySelector('time')!.textContent).toContain('2 mai 2026');
    expect(tavern.main().textContent).toContain('Page 1 sur 2');

    field<HTMLButtonElement>('[aria-label="Page suivante"]').click();
    tavern.backend
      .expectOne((r) => list(r) && r.params.get('page') === '1' && r.params.get('size') === '20')
      .flush(notesPage([historicalNote({ id: 'n0', content: 'Tout début.' })], 2, 1));
    await tavern.render();
    expect(contents()).toEqual(['Tout début.']);
  });

  it('renders the content as text, never as markup', async () => {
    await open();
    await answer([historicalNote({ content: '<b>gras</b>' })]);
    expect(items()[0].querySelector('b')).toBeNull();
    expect(contents()).toEqual(['<b>gras</b>']);
  });

  it('says so when the chronicle is empty', async () => {
    await open();
    await answer([]);
    expect(tavern.main().textContent).toContain('La chronique est encore vierge.');
  });

  it('a participant adds a note, dated today by default', async () => {
    await open();
    await answer([]);
    tavern.button('Ajouter une note').click();
    await tavern.render();
    expect(field<HTMLInputElement>('input[type="date"]').value).toBe(today());
    expect(tavern.main().querySelector('select')).toBeNull();
    await type('textarea', 'Une nouvelle page.');
    expect(tavern.main().textContent).toContain('18 / 5000');
    tavern.button('Inscrire').click();
    const post = tavern.backend.expectOne(
      (r) => r.method === 'POST' && /\/historical-notes$/.test(r.url),
    );
    expect(post.request.body).toEqual({
      adventureId: 'a1',
      content: 'Une nouvelle page.',
      date: today(),
    });
    post.flush(historicalNote());
    await tavern.render();
    tavern.backend.expectOne(list).flush(notesPage([historicalNote()]));
    await tavern.render();
    expect(items().length).toBe(1);
    expect(tavern.main().querySelector('textarea')).toBeNull();
  });

  it('refuses an empty content and a date out of range', async () => {
    await open();
    await answer([]);
    tavern.button('Ajouter une note').click();
    await tavern.render();
    await type('input[type="date"]', '1850-01-01');
    tavern.button('Inscrire').click();
    await tavern.render();
    tavern.backend.expectNone((r) => r.method === 'POST');
    expect(tavern.main().textContent).toContain('Ce champ est obligatoire.');
    expect(tavern.main().textContent).toContain('entre 1900 et 2200');
  });

  it('explains the limit of 500 notes', async () => {
    await open();
    await answer([]);
    tavern.button('Ajouter une note').click();
    await tavern.render();
    await type('textarea', 'x');
    tavern.button('Inscrire').click();
    tavern.backend.expectOne((r) => r.method === 'POST').flush({ status: 409 }, conflict);
    await tavern.render();
    expect(tavern.main().textContent).toContain('limite de 500 notes');
  });

  it('edits a note in place', async () => {
    await open();
    await answer();
    tavern.button('Modifier').click();
    await tavern.render();
    expect(field<HTMLTextAreaElement>('textarea').value).toBe('Le pont est tombé.');
    await type('textarea', 'Le pont est réparé.');
    tavern.button('Enregistrer').click();
    const patch = tavern.backend.expectOne(
      (r) => r.method === 'PATCH' && /\/historical-notes\/n1$/.test(r.url),
    );
    expect(patch.request.body).toEqual({ content: 'Le pont est réparé.', date: '2026-03-14' });
    patch.flush(historicalNote({ content: 'Le pont est réparé.' }));
    await tavern.render();
    tavern.backend
      .expectOne(list)
      .flush(notesPage([historicalNote({ content: 'Le pont est réparé.' })]));
    await tavern.render();
    expect(contents()).toEqual(['Le pont est réparé.']);
    expect(tavern.main().querySelector('textarea')).toBeNull();
  });

  it('cancelling an edit leaves the note as it was', async () => {
    await open();
    await answer();
    tavern.button('Modifier').click();
    await tavern.render();
    tavern.button('Annuler').click();
    await tavern.render();
    expect(tavern.main().querySelector('textarea')).toBeNull();
    expect(contents()).toEqual(['Le pont est tombé.']);
  });

  it('a concurrent change offers to reload', async () => {
    await open();
    await answer();
    tavern.button('Modifier').click();
    await tavern.render();
    tavern.button('Enregistrer').click();
    tavern.backend.expectOne((r) => r.method === 'PATCH').flush({ status: 409 }, conflict);
    await tavern.render();
    const notifier = TestBed.inject(NotifierService);
    expect(notifier.toasts()[0].action?.label).toBe('Recharger');
    notifier.act(notifier.toasts()[0]);
    tavern.backend.expectOne(list).flush(notesPage([historicalNote({ content: 'Autre.' })]));
    await tavern.render();
    expect(contents()).toEqual(['Autre.']);
  });

  it('a refused edit shows a toast', async () => {
    await open();
    await answer();
    tavern.button('Modifier').click();
    await tavern.render();
    tavern.button('Enregistrer').click();
    tavern.backend
      .expectOne((r) => r.method === 'PATCH')
      .flush({ status: 403 }, { status: 403, statusText: 'Forbidden' });
    await tavern.render();
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('droits');
  });

  it('deleting a note sends confirm true after confirmation', async () => {
    await open();
    await answer();
    const confirm = vi.spyOn(TestBed.inject(ConfirmService), 'confirm').mockReturnValue(of(true));
    tavern.button('Supprimer').click();
    expect(confirm).toHaveBeenCalled();
    const del = tavern.backend.expectOne(
      (r) => r.method === 'DELETE' && /\/historical-notes\/n1$/.test(r.url),
    );
    expect(del.request.body).toEqual({ confirm: true });
    del.flush(null, noContent);
    await tavern.render();
    tavern.backend.expectOne(list).flush(notesPage([]));
    await tavern.render();
    expect(items().length).toBe(0);
  });

  it('a refused deletion shows a toast', async () => {
    await open();
    await answer();
    vi.spyOn(TestBed.inject(ConfirmService), 'confirm').mockReturnValue(of(true));
    tavern.button('Supprimer').click();
    tavern.backend
      .expectOne((r) => r.method === 'DELETE')
      .flush({ status: 403 }, { status: 403, statusText: 'Forbidden' });
    await tavern.render();
    expect(TestBed.inject(NotifierService).toasts()[0].message).toContain('droits');
    expect(items().length).toBe(1);
  });

  it('keeps the note when the reader does not confirm', async () => {
    await open();
    await answer();
    vi.spyOn(TestBed.inject(ConfirmService), 'confirm').mockReturnValue(of(false));
    tavern.button('Supprimer').click();
    tavern.backend.expectNone((r) => r.method === 'DELETE');
  });

  it('goes back a page when the last note of a page is deleted', async () => {
    await open();
    await answer([historicalNote()], 2);
    field<HTMLButtonElement>('[aria-label="Page suivante"]').click();
    tavern.backend
      .expectOne((r) => list(r) && r.params.get('page') === '1')
      .flush(notesPage([historicalNote({ id: 'n0' })], 2, 1));
    await tavern.render();
    vi.spyOn(TestBed.inject(ConfirmService), 'confirm').mockReturnValue(of(true));
    tavern.button('Supprimer').click();
    tavern.backend.expectOne((r) => r.method === 'DELETE').flush(null, noContent);
    await tavern.render();
    tavern.backend.expectOne(list).flush(notesPage([], 1, 1));
    await tavern.render();
    tavern.backend
      .expectOne((r) => list(r) && r.params.get('page') === '0')
      .flush(notesPage([historicalNote()], 1, 0));
    await tavern.render();
    expect(items().length).toBe(1);
  });

  it('asks nothing of the API for someone who is not a participant', async () => {
    await open({ players: ['p1'], creatorId: 'master' });
    tavern.backend.expectNone(list);
    tavern.backend.match(accounts).forEach((r) => r.flush({}));
    expect(tavern.router.url).toBe('/tavern/adventures/a1/overview');
  });

  it('offers to retry when the chronicle cannot be loaded', async () => {
    await open();
    tavern.backend.expectOne(list).flush({}, { status: 500, statusText: 'Server' });
    await tavern.render();
    expect(field('[role="alert"]').textContent).toContain('Impossible');
    tavern.button('Réessayer').click();
    await answer([]);
    expect(tavern.main().textContent).toContain('encore vierge');
  });

  it('shows the notes of the other adventure after navigating between two', async () => {
    await open();
    await answer([historicalNote({ content: 'Pour a1.' })]);
    await tavern.go('/tavern/adventures/a2/chronicle');
    tavern.backend
      .expectOne((r) => r.method === 'GET' && /\/adventures\/a2$/.test(r.url))
      .flush(adventure({ id: 'a2', players: ['me'] }));
    await tavern.render();
    tavern.backend
      .expectOne((r) => /\/historical-notes$/.test(r.url) && r.params.get('adventureId') === 'a2')
      .flush(notesPage([historicalNote({ id: 'x', content: 'Pour a2.' })]));
    await tavern.render();
    expect(contents()).toEqual(['Pour a2.']);
  });

  it.each([
    ['2201-01-01', false],
    ['20000-01-01', false],
    ['1899-12-31', false],
    ['1900-01-01', true],
    ['2200-12-31', true],
  ])('date %s accepted: %s', async (date, accepted) => {
    await open();
    await answer([]);
    tavern.button('Ajouter une note').click();
    await tavern.render();
    await type('input[type="date"]', date);
    await type('textarea', 'x');
    tavern.button('Inscrire').click();
    await tavern.render();
    const posts = tavern.backend.match((r) => r.method === 'POST');
    expect(posts.length).toBe(accepted ? 1 : 0);
    posts.forEach((r) => r.flush(historicalNote()));
    if (accepted) {
      await tavern.render();
      tavern.backend.expectOne(list).flush(notesPage([]));
    } else {
      expect(tavern.main().textContent).toContain('entre 1900 et 2200');
    }
  });
});
