import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '../../../core/api/api.providers';
import { NotifierService } from '../../../core/errors/notifier.service';
import { LibraryTestbed, setupLibrary } from '../library.testing';
import { FakeRuleset } from '../rulesets/fake.ruleset';

describe('SpellBookListPage', () => {
  let library: LibraryTestbed;
  let backend: HttpTestingController;

  const mine = (r: { url: string }) => /spellbooks\/mine$/.test(r.url);

  async function open(books: object[] = [], url = '/library/grimoires'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    library = await setupLibrary({
      signedIn: true,
      rulesets: [
        new FakeRuleset({ id: 'dnd5e-2014', label: 'DnD 5e — SRD 2014' }),
        new FakeRuleset({ id: 'dnd5e-2024', label: 'DnD 5e — SRD 2024' }),
        new FakeRuleset({ id: 'no-spells', label: 'Sans sorts', spells: false }),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    await library.go(url);
    backend.expectOne((r) => r.method === 'GET' && mine(r)).flush(books);
    await library.render();
  }

  const main = () => library.root.querySelector('main')!;
  const button = (label: string) =>
    [...main().querySelectorAll('button')].find((b) => b.textContent?.trim() === label)!;

  const create = () =>
    main().querySelector<HTMLButtonElement>('[aria-label="Créer un grimoire"]')!.click();

  function type(selector: string, value: string): void {
    const input = main().querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function chooseFile(file: File): void {
    const input = main().querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
  }

  afterEach(() => backend.verify());

  it('lists the books with their ruleset label and spell count', async () => {
    await open([
      { id: 'a', name: 'Arcane', description: 'd', ruleset: 'dnd5e-2024', spellList: ['x', 'y'] },
      { id: 'b', name: 'Vieux', description: 'd', ruleset: 'gone', spellList: [] },
    ]);
    const cards = [...main().querySelectorAll('.book')];
    expect(cards[0].textContent).toContain('Arcane');
    expect(cards[0].textContent).toContain('DnD 5e — SRD 2024');
    expect(cards[0].textContent).toContain('2 sorts');
    expect(cards[0].querySelector('a')?.getAttribute('href')).toBe('/library/grimoires/a');
    expect(cards[1].textContent).toContain('gone');
    expect(cards[1].textContent).toContain('0 sort');
    expect(cards[1].textContent).not.toContain('0 sorts');
  });

  it('lists the books as a table once chosen', async () => {
    await open([
      { id: 'a', name: 'Arcane', description: 'd', ruleset: 'dnd5e-2024', spellList: ['x', 'y'] },
    ]);
    main().querySelector<HTMLButtonElement>('[aria-label="Afficher en tableau"]')!.click();
    await library.render();
    expect(main().querySelector('.book')).toBeNull();
    const cells = [...main().querySelectorAll('tbody td')].map((td) => td.textContent?.trim());
    expect(cells).toEqual(['Arcane', 'DnD 5e — SRD 2024', '2 sorts']);
    expect(main().querySelector('tbody a')?.getAttribute('href')).toBe('/library/grimoires/a');
  });

  it('creates a spell book with the selected ruleset', async () => {
    await open();
    create();
    await library.render();
    const options = [...main().querySelectorAll('select option')].map((o) => o.textContent?.trim());
    expect(options).toEqual(['DnD 5e — SRD 2014', 'DnD 5e — SRD 2024']);
    type('input[formControlName="name"]', ' Mes sorts ');
    type('textarea[formControlName="description"]', 'Pour la guerre');
    const select = main().querySelector<HTMLSelectElement>('select')!;
    select.value = 'dnd5e-2024';
    select.dispatchEvent(new Event('change'));
    main().querySelector('form')!.dispatchEvent(new Event('submit'));
    const request = backend.expectOne((r) => r.method === 'POST' && /spellbooks$/.test(r.url));
    expect(request.request.body).toEqual({
      name: 'Mes sorts',
      description: 'Pour la guerre',
      ruleset: 'dnd5e-2024',
    });
    request.flush({
      id: 'n',
      name: 'Mes sorts',
      description: 'Pour la guerre',
      ruleset: 'dnd5e-2024',
      spellList: [],
    });
    await library.render();
    expect(main().textContent).toContain('Mes sorts');
    expect(main().querySelector('form')).toBeNull();
  });

  it('defaults the ruleset to the selected one', async () => {
    await open();
    create();
    await library.render();
    expect(main().querySelector<HTMLSelectElement>('select')!.value).toBe('dnd5e-2014');
  });

  it('opens the creation form on the ruleset asked in the query', async () => {
    await open([], '/library/grimoires?ruleset=dnd5e-2024');
    expect(main().querySelector<HTMLSelectElement>('select')!.value).toBe('dnd5e-2024');
  });

  it('refuses an empty name without calling the API', async () => {
    await open();
    create();
    await library.render();
    main().querySelector('form')!.dispatchEvent(new Event('submit'));
    await library.render();
    expect(main().textContent).toContain('Ce champ est obligatoire.');
  });

  it('create shows the plan refusal on a 409', async () => {
    await open();
    create();
    await library.render();
    type('input[formControlName="name"]', 'A');
    type('textarea[formControlName="description"]', 'B');
    main().querySelector('form')!.dispatchEvent(new Event('submit'));
    backend
      .expectOne((r) => r.method === 'POST' && /spellbooks$/.test(r.url))
      .flush(
        {
          type: 'urn:dndgate:plan-limit-reached',
          limit: 'spellBooks',
          max: 2,
          planId: 'adventurer',
          upgradePlanId: 'hero',
          decidedBy: 'account',
        },
        { status: 409, statusText: 'Conflict' },
      );
    const [toast] = TestBed.inject(NotifierService).toasts();
    expect(toast.message).toBe(
      'Création du grimoire : Limite atteinte : Grimoires (2 au maximum). L’offre Héros le permet.',
    );
    expect(toast.action?.label).toBe('Voir les offres');
  });

  it('import sends the chosen file as multipart', async () => {
    await open();
    const input = main().querySelector<HTMLInputElement>('input[type="file"]')!;
    expect(input.accept).toBe('application/json,.json');
    const click = vi.spyOn(input, 'click');
    button('Importer').click();
    expect(click).toHaveBeenCalled();

    chooseFile(new File(['{"name":"x"}'], 'book.json', { type: 'application/json' }));
    const request = backend.expectOne((r) => r.method === 'POST' && /import-file$/.test(r.url));
    const body = request.request.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect((body.get('file') as File).name).toBe('book.json');
    request.flush({
      id: 'i',
      name: 'x',
      description: 'd',
      ruleset: 'dnd5e-2014',
      spellList: ['a'],
    });
    await library.render();
    expect(main().querySelectorAll('.book').length).toBe(1);
    expect(TestBed.inject(NotifierService).toasts()[0].kind).toBe('success');
  });

  it('import shows field errors from a 400', async () => {
    await open();
    chooseFile(new File(['{}'], 'book.json', { type: 'application/json' }));
    backend
      .expectOne((r) => /import-file$/.test(r.url))
      .flush(
        { status: 400, errors: [{ field: 'name', message: 'must not be blank' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    await library.render();
    const box = main().querySelector('.import-errors')!;
    expect(box.getAttribute('role')).toBe('alert');
    expect(box.textContent).toContain('name');
    expect(box.textContent).toContain('must not be blank');
  });

  it('import shows the plan refusal on a 409 with a plan', async () => {
    await open();
    chooseFile(new File(['{}'], 'b.json'));
    backend
      .expectOne((r) => /import-file$/.test(r.url))
      .flush(
        {
          type: 'urn:dndgate:plan-limit-reached',
          limit: 'spellBooks',
          max: 2,
          planId: 'adventurer',
          upgradePlanId: 'hero',
          decidedBy: 'account',
        },
        { status: 409, statusText: 'Conflict' },
      );
    const [toast] = TestBed.inject(NotifierService).toasts();
    expect(toast.message).toBe(
      'Import du grimoire : Limite atteinte : Grimoires (2 au maximum). L’offre Héros le permet.',
    );
    expect(toast.action?.label).toBe('Voir les offres');
  });

  it('import explains the ceiling of 1 000 spells on a 409 without a plan', async () => {
    await open();
    chooseFile(new File(['{}'], 'b.json'));
    backend
      .expectOne((r) => /import-file$/.test(r.url))
      .flush({ status: 409 }, { status: 409, statusText: 'Conflict' });
    expect(TestBed.inject(NotifierService).toasts()[0].message).toBe(
      'Conflit : un grimoire contient au plus 1 000 sorts.',
    );
  });

  it('shows a retry state when the list cannot be loaded', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    library = await setupLibrary({ signedIn: true });
    backend = TestBed.inject(HttpTestingController);
    await library.go('/library/grimoires');
    backend.expectOne(mine).flush({}, { status: 500, statusText: 'Server Error' });
    await library.render();
    expect(main().querySelector('[role="alert"]')?.textContent).toContain('Impossible de charger');
    button('Réessayer').click();
    backend.expectOne(mine).flush([]);
  });
});
