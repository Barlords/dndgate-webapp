import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '../../../core/api/api.providers';
import { NotifierService } from '../../../core/errors/notifier.service';
import { MySpellBooksStore } from './my-spellbooks.store';

const BOOK = { id: 'b1', name: 'Arcane', ruleset: 'dnd5e-2014', spellList: ['light'] };

describe('MySpellBooksStore', () => {
  let store: MySpellBooksStore;
  let backend: HttpTestingController;
  let injector: Injector;

  const mine = (r: { method: string; url: string }) =>
    r.method === 'GET' && /\/spellbooks\/mine$/.test(r.url);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    // Provided per page in the app: a child injector stands for the page.
    injector = Injector.create({
      providers: [MySpellBooksStore],
      parent: TestBed.inject(Injector),
    });
    store = injector.get(MySpellBooksStore);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('reads the books once however many buttons ask', () => {
    store.load();
    store.load();
    backend.expectOne(mine).flush([BOOK]);
    store.load();
    backend.expectNone(mine);
    expect(store.books()).toEqual([BOOK]);
  });

  it('reads them again after a failure', () => {
    const error = vi.spyOn(TestBed.inject(NotifierService), 'error');
    store.load();
    backend.expectOne(mine).flush('x', { status: 500, statusText: 'Error' });
    expect(error).toHaveBeenCalled();
    store.load();
    backend.expectOne(mine).flush([BOOK]);
    expect(store.books()).toEqual([BOOK]);
  });

  it('keeps both spells when two are added to one book at the same time', () => {
    store.load();
    backend.expectOne(mine).flush([BOOK]);
    store.toggle(store.books()[0], 'fireball');
    store.toggle(store.books()[0], 'shield');
    expect(store.isPending(store.books()[0], 'fireball')).toBe(true);
    const [fireball, shield] = backend.match((r) => r.method === 'POST');
    // Each answer only knows of its own spell; the book keeps both.
    shield.flush({ ...BOOK, spellList: ['light', 'shield'] });
    fireball.flush({ ...BOOK, spellList: ['light', 'fireball'] });
    expect([...store.books()[0].spellList!].sort()).toEqual(['fireball', 'light', 'shield']);
    expect(store.isPending(store.books()[0], 'fireball')).toBe(false);
  });

  it('removes a spell the book holds and ignores a repeat while it is on its way', () => {
    store.load();
    backend.expectOne(mine).flush([BOOK]);
    store.toggle(store.books()[0], 'light');
    store.toggle(store.books()[0], 'light');
    backend
      .expectOne((r) => r.method === 'DELETE' && /spellbooks\/b1\/spells\/light$/.test(r.url))
      .flush(null, { status: 204, statusText: 'No Content' });
    expect(store.holds(store.books()[0], 'light')).toBe(false);
  });

  it('drops an answer arriving once the page is gone', () => {
    const success = vi.spyOn(TestBed.inject(NotifierService), 'success');
    store.load();
    backend.expectOne(mine).flush([BOOK]);
    store.toggle(store.books()[0], 'fireball');
    (injector as Injector & { destroy(): void }).destroy();
    const add = backend.expectOne((r) => r.method === 'POST');
    expect(add.cancelled).toBe(true);
    expect(success).not.toHaveBeenCalled();
  });
});
