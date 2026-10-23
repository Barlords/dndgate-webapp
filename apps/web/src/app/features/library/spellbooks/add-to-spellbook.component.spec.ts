import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '../../../core/api/api.providers';
import { NotifierService } from '../../../core/errors/notifier.service';
import { LibraryTestbed, setupLibrary } from '../library.testing';
import { FakeRuleset } from '../rulesets/fake.ruleset';

const BOOKS = [
  { id: 'b1', name: 'Arcane', ruleset: 'dnd5e-2014', spellList: ['fireball'] },
  { id: 'b2', name: 'Divin', ruleset: 'dnd5e-2014', spellList: [] },
  { id: 'b3', name: 'Nouveau monde', ruleset: 'dnd5e-2024', spellList: [] },
];

describe('AddToSpellbook', () => {
  let library: LibraryTestbed;
  let backend: HttpTestingController;

  async function open(signedIn = true, books: object[] = BOOKS): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    library = await setupLibrary({
      signedIn,
      rulesets: [
        new FakeRuleset({ id: 'dnd5e-2014', label: 'DnD 5e — SRD 2014' }),
        new FakeRuleset({ id: 'dnd5e-2024', label: 'DnD 5e — SRD 2024' }),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    await library.go('/library/dnd5e-2014/spells/fireball');
    if (signedIn) {
      backend
        .expectOne((r) => r.method === 'GET' && /\/spellbooks\/mine$/.test(r.url))
        .flush(books);
      await library.render();
    }
  }

  const trigger = () => library.root.querySelector<HTMLButtonElement>('.add-to-book__trigger');
  const items = () => [...document.querySelectorAll<HTMLElement>('[role="menuitemcheckbox"]')];

  async function openMenu(): Promise<void> {
    trigger()!.click();
    await library.render();
  }

  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
  });

  it('offers only spell books of the same ruleset', async () => {
    await open();
    await openMenu();
    expect(items().map((i) => i.textContent!.replace('✓', '').trim())).toEqual(['Arcane', 'Divin']);
    expect(items()[0].getAttribute('aria-checked')).toBe('true');
    expect(items()[1].getAttribute('aria-checked')).toBe('false');
  });

  it('adds then removes a spell', async () => {
    await open();
    const notifier = TestBed.inject(NotifierService);
    const success = vi.spyOn(notifier, 'success');
    await openMenu();
    items()[1].click();
    const add = backend.expectOne(
      (r) => r.method === 'POST' && /spellbooks\/b2\/spells$/.test(r.url),
    );
    expect(add.request.body).toEqual({ spellIndex: 'fireball' });
    add.flush({ ...BOOKS[1], spellList: ['fireball'] });
    await library.render();
    expect(success).toHaveBeenCalledWith('Ajouté à Divin');

    await openMenu();
    expect(items()[1].getAttribute('aria-checked')).toBe('true');
    items()[1].click();
    const remove = backend.expectOne(
      (r) => r.method === 'DELETE' && /spellbooks\/b2\/spells\/fireball$/.test(r.url),
    );
    remove.flush(null, { status: 204, statusText: 'No Content' });
    await library.render();
    expect(success).toHaveBeenCalledWith('Retiré de Divin');
    await openMenu();
    expect(items()[1].getAttribute('aria-checked')).toBe('false');
  });

  it('ignores a second click while a request is in flight', async () => {
    await open();
    await openMenu();
    items()[1].click();
    await openMenu();
    items()[1].click();
    const calls = backend.match((r) => r.method === 'POST');
    expect(calls.length).toBe(1);
    calls[0].flush({ ...BOOKS[1], spellList: ['fireball'] });
  });

  it('reports a failure and leaves the book unchanged', async () => {
    await open();
    const error = vi.spyOn(TestBed.inject(NotifierService), 'error');
    await openMenu();
    items()[1].click();
    backend
      .expectOne((r) => r.method === 'POST')
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    await library.render();
    expect(error).toHaveBeenCalled();
    await openMenu();
    expect(items()[1].getAttribute('aria-checked')).toBe('false');
  });

  it('is hidden when logged out', async () => {
    await open(false);
    expect(trigger()).toBeNull();
  });

  it('suggests creating a spell book when none fits', async () => {
    await open(true, [BOOKS[2]]);
    await openMenu();
    expect(items().length).toBe(0);
    const link = document.querySelector<HTMLAnchorElement>('a.add-to-book__create');
    expect(link?.getAttribute('href')).toBe('/library/grimoires?ruleset=dnd5e-2014');
    expect(link?.getAttribute('role')).toBe('menuitem');
    expect(link?.textContent).toContain('Créer un grimoire');
  });

  it('reaches the create item with the keyboard and navigates on activation', async () => {
    await open(true, [BOOKS[2]]);
    await openMenu();
    const menu = document.querySelector<HTMLElement>('[role="menu"]')!;
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    await library.render();
    const link = document.querySelector<HTMLAnchorElement>('.add-to-book__create')!;
    expect(document.activeElement).toBe(link);
    link.click();
    await library.render();
    await library.harness.fixture.whenStable();
    expect(library.router.url).toBe('/library/grimoires?ruleset=dnd5e-2014');
    backend.match(() => true);
  });
});
