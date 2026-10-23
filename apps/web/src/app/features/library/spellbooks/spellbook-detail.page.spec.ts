import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { provideApi } from '../../../core/api/api.providers';
import { NotifierService } from '../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { LibraryTestbed, setupLibrary } from '../library.testing';
import { FakeRuleset } from '../rulesets/fake.ruleset';
import { EntrySummary, Ruleset } from '../rulesets/ruleset.model';

const BOOK = {
  id: 'b1',
  name: 'Arcane',
  description: 'Mes sorts',
  ruleset: 'dnd5e-2014',
  spellList: ['shield', 'fireball', 'ghost', 'light', 'eclair'],
};

describe('SpellBookDetailPage', () => {
  let library: LibraryTestbed;
  let backend: HttpTestingController;

  async function setup(rulesets: Ruleset[]): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
    });
    library = await setupLibrary({ signedIn: true, rulesets });
    backend = TestBed.inject(HttpTestingController);
  }

  async function open(book: object = BOOK, rulesets?: Ruleset[]): Promise<void> {
    await setup(
      rulesets ?? [
        new FakeRuleset({ id: 'dnd5e-2014', label: 'DnD 5e — SRD 2014' }),
        new FakeRuleset({ id: 'dnd5e-2024', label: 'DnD 5e — SRD 2024' }),
      ],
    );
    await load(book);
  }

  async function load(book: object): Promise<void> {
    await library.go('/library/grimoires/b1');
    backend.expectOne((r) => r.method === 'GET' && /spellbooks\/b1$/.test(r.url)).flush(book);
    await library.render();
  }

  const main = () => library.root.querySelector('main')!;
  const button = (label: string) =>
    [...main().querySelectorAll('button')].find((b) => b.textContent?.trim() === label)!;
  const rows = () => [...main().querySelectorAll('.spell')].map((r) => r.textContent!);

  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('shows the spells sorted by level then name, with level and school', async () => {
    await open();
    const shown = rows();
    expect(shown.length).toBe(5);
    expect(shown[0]).toContain('Light');
    expect(shown[0]).toContain('Tour de magie');
    expect(shown[1]).toContain('Shield');
    expect(shown[1]).toContain('Niveau 1');
    expect(shown[1]).toContain('Abjuration');
    expect(shown[2]).toContain('Éclair');
    expect(shown[3]).toContain('Fireball');
    expect(shown[4]).toContain('Sort introuvable (ghost)');
    expect(main().querySelector('a[href="/library/dnd5e-2014/spells/fireball"]')).not.toBeNull();
    expect(main().textContent).toContain('DnD 5e — SRD 2014');
  });

  it('sets the breadcrumb to the book name', async () => {
    await open();
    expect(
      TestBed.inject(BreadcrumbService)
        .crumbs()
        .map((c) => c.label),
    ).toEqual(['Bibliothèque', 'Mes grimoires', 'Arcane']);
  });

  it('resolves spells in the spell book ruleset, not the selected one', async () => {
    const first = new FakeRuleset({ id: 'dnd5e-2014' });
    const second = new FakeRuleset({ id: 'dnd5e-2024', label: 'DnD 5e — SRD 2024' });
    const only2024: EntrySummary[] = [
      { index: 'only-2024', name: 'Nouveau sort', fields: { level: 2, school: 'Illusion' } },
    ];
    vi.spyOn(second, 'list').mockReturnValue(of(only2024));
    const listFirst = vi.spyOn(first, 'list');
    await setup([first, second]);
    await library.go('/library/dnd5e-2014');
    await load({ ...BOOK, ruleset: 'dnd5e-2024', spellList: ['only-2024'] });
    expect(rows()[0]).toContain('Nouveau sort');
    expect(rows()[0]).toContain('Niveau 2');
    expect(main().querySelector('a[href="/library/dnd5e-2024/spells/only-2024"]')).not.toBeNull();
    expect(listFirst).not.toHaveBeenCalled();
  });

  it('shows spells by index when the ruleset is not registered', async () => {
    await open({ ...BOOK, ruleset: 'gone', spellList: ['b', 'a'] });
    expect(main().textContent).toContain('Jeu de règles indisponible');
    const shown = rows();
    expect(shown[0]).toContain('a');
    expect(shown[1]).toContain('b');
    expect(main().querySelector('.spell a')).toBeNull();
  });

  it('shows unknown spells and lets them be removed', async () => {
    await open();
    const row = [...main().querySelectorAll('.spell')].find((r) =>
      r.textContent!.includes('Sort introuvable (ghost)'),
    )!;
    (row.querySelector('button') as HTMLButtonElement).click();
    backend
      .expectOne((r) => r.method === 'DELETE' && /spellbooks\/b1\/spells\/ghost$/.test(r.url))
      .flush(null, { status: 204, statusText: 'No Content' });
    await library.render();
    expect(rows().length).toBe(4);
    expect(main().textContent).not.toContain('ghost');
  });

  it('keeps the rest of the book when the spell list cannot be read', async () => {
    const ruleset = new FakeRuleset({ id: 'dnd5e-2014' });
    vi.spyOn(ruleset, 'list').mockReturnValue(throwError(() => new Error('down')));
    await open({ ...BOOK, spellList: ['shield'] }, [ruleset]);
    expect(main().textContent).toContain('Arcane');
    expect(main().textContent).toContain('Détails des sorts indisponibles');
    expect(rows().length).toBe(1);
  });

  it('shows a not-found state for an unknown book', async () => {
    await setup([new FakeRuleset()]);
    await library.go('/library/grimoires/b1');
    backend
      .expectOne((r) => /spellbooks\/b1$/.test(r.url))
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    await library.render();
    expect(main().querySelector('[role="alert"]')?.textContent).toContain('Grimoire introuvable');
  });

  describe('editing', () => {
    function submitEdit(name: string, description: string): void {
      button('Modifier').click();
      library.harness.detectChanges();
      const nameInput = main().querySelector<HTMLInputElement>('input[formControlName="name"]')!;
      nameInput.value = name;
      nameInput.dispatchEvent(new Event('input'));
      const text = main().querySelector<HTMLTextAreaElement>(
        'textarea[formControlName="description"]',
      )!;
      text.value = description;
      text.dispatchEvent(new Event('input'));
      main().querySelector('form')!.dispatchEvent(new Event('submit'));
    }

    it('does not let the ruleset be changed', async () => {
      await open();
      button('Modifier').click();
      library.harness.detectChanges();
      expect(main().querySelector('select')).toBeNull();
    });

    it('patches only what changed', async () => {
      await open();
      submitEdit('Nouveau nom', 'Mes sorts');
      const request = backend.expectOne(
        (r) => r.method === 'PATCH' && /spellbooks\/b1$/.test(r.url),
      );
      expect(request.request.body).toEqual({ name: 'Nouveau nom' });
      request.flush({ ...BOOK, name: 'Nouveau nom' });
      await library.render();
      expect(main().querySelector('h1')?.textContent).toContain('Nouveau nom');
      expect(main().querySelector('form')).toBeNull();
    });

    it('offers to reload on a 409', async () => {
      await open();
      submitEdit('Autre', 'Mes sorts');
      backend
        .expectOne((r) => r.method === 'PATCH')
        .flush({ status: 409 }, { status: 409, statusText: 'Conflict' });
      const toast = TestBed.inject(NotifierService).toasts()[0];
      expect(toast.action?.label).toBe('Recharger');
      toast.action!.run();
      backend.expectOne((r) => r.method === 'GET' && /spellbooks\/b1$/.test(r.url)).flush(BOOK);
    });

    it('shows the field errors of a 400', async () => {
      await open();
      submitEdit('Autre', 'Mes sorts');
      backend
        .expectOne((r) => r.method === 'PATCH')
        .flush(
          { status: 400, errors: [{ field: 'name', message: 'too long' }] },
          { status: 400, statusText: 'Bad Request' },
        );
      library.harness.detectChanges();
      expect(main().textContent).toContain('too long');
    });
  });

  describe('delete', () => {
    it('delete requires the exact name', async () => {
      await open();
      const typeToConfirm = vi
        .spyOn(TestBed.inject(ConfirmService), 'typeToConfirm')
        .mockReturnValue(of(' Arcane '));
      button('Supprimer').click();
      expect(typeToConfirm.mock.calls[0][0]).toMatchObject({
        expected: 'Arcane',
        inputType: 'text',
      });
      const request = backend.expectOne(
        (r) => r.method === 'DELETE' && /spellbooks\/b1$/.test(r.url),
      );
      expect(request.request.body).toEqual({ confirmation: ' Arcane ' });
      request.flush(null, { status: 204, statusText: 'No Content' });
      await library.render();
      expect(library.router.url).toBe('/library/grimoires');
      backend.expectOne((r) => /spellbooks\/mine$/.test(r.url)).flush([]);
    });

    it('does nothing when the confirmation is cancelled', async () => {
      await open();
      vi.spyOn(TestBed.inject(ConfirmService), 'typeToConfirm').mockReturnValue(of(null));
      button('Supprimer').click();
    });
  });

  it('export downloads a json file named after the book', async () => {
    await open();
    const created: Blob[] = [];
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: (blob: Blob) => (created.push(blob), 'blob:x'),
        revokeObjectURL: () => undefined,
      }),
    );
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    button('Exporter').click();
    const dto = {
      name: 'Arcane',
      description: 'Mes sorts',
      spellList: ['shield'],
      ruleset: 'dnd5e-2014',
    };
    backend.expectOne((r) => /spellbooks\/b1\/export-file$/.test(r.url)).flush(dto);
    expect(click).toHaveBeenCalled();
    expect(JSON.parse(await created[0].text())).toEqual(dto);
  });
});
