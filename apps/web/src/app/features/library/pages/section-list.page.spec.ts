import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NEVER, of, throwError } from 'rxjs';
import { provideApi } from '../../../core/api/api.providers';
import { LibraryTestbed, setupLibrary } from '../library.testing';
import { FakeRuleset } from '../rulesets/fake.ruleset';
import { EntrySummary } from '../rulesets/ruleset.model';

describe('SectionListPage', () => {
  let ruleset: FakeRuleset;
  let library: LibraryTestbed;

  async function open(url: string): Promise<void> {
    library = await setupLibrary({ rulesets: [ruleset] });
    await library.go(url);
  }

  beforeEach(() => {
    ruleset = new FakeRuleset();
  });

  const names = () =>
    [...library.root.querySelectorAll('tbody tr')].map((row) =>
      row.querySelector('a')?.textContent?.trim(),
    );
  const query = () => library.router.parseUrl(library.router.url).queryParams;
  const search = () => library.root.querySelector<HTMLInputElement>('input[type="search"]')!;
  const toggle = () =>
    library.root.querySelector<HTMLButtonElement>('.table__filters-toggle button')!;
  const group = (legend: string) =>
    [...library.root.querySelectorAll('.panel__group')].find(
      (fieldset) => fieldset.querySelector('legend')?.textContent?.trim() === legend,
    )!;
  const choices = (legend: string) =>
    [...group(legend).querySelectorAll('label')].map((label) => label.textContent?.trim());
  const box = (legend: string, choice: string) =>
    [...group(legend).querySelectorAll('label')]
      .find((label) => label.textContent?.trim() === choice)!
      .querySelector('input')!;
  const headers = () =>
    [...library.root.querySelectorAll('thead th')].map((cell) =>
      cell.textContent?.replace(/[▲▼]/g, '').trim(),
    );

  async function settle(): Promise<void> {
    await library.harness.fixture.whenStable();
    await library.render();
  }

  async function change(element: HTMLInputElement | HTMLSelectElement, value: string) {
    element.value = value;
    element.dispatchEvent(new Event(element instanceof HTMLInputElement ? 'input' : 'change'));
    await settle();
  }

  async function openFilters(): Promise<void> {
    toggle().click();
    await settle();
  }

  async function tick(legend: string, choice: string): Promise<void> {
    box(legend, choice).click();
    await settle();
  }

  async function sortBy(label: string) {
    const header = [...library.root.querySelectorAll('thead th')].find((cell) =>
      cell.textContent?.includes(label),
    )!;
    header.querySelector('button')!.click();
    await settle();
    return header;
  }

  it('says when the entries of the section are not in the reader’s language', async () => {
    ruleset = new FakeRuleset({ untranslated: ['monsters'] });
    await open('/library/dnd5e-2014/monsters');
    expect(library.root.querySelector('dg-untranslated-note [role="note"]')?.textContent).toBe(
      'Pas encore traduit dans ta langue : affiché en anglais.',
    );

    await library.go('/library/dnd5e-2014/spells');
    expect(library.root.querySelector('dg-untranslated-note')).toBeNull();
  });

  it('lists the entries with their columns and links them to their page', async () => {
    await open('/library/dnd5e-2014/monsters');
    expect(library.root.querySelector('h1')?.textContent).toContain('Monstres');
    expect(headers()).toEqual(['Nom', 'Mots-clés', 'Légendaire']);
    const first = [...library.root.querySelectorAll('tbody tr')][0];
    const cells = [...first.querySelectorAll('td')].map((cell) => cell.textContent?.trim());
    expect(cells).toEqual(['Dragon', 'big, old', 'Oui']);
    expect(first.querySelector('a')?.getAttribute('href')).toBe(
      '/library/dnd5e-2014/monsters/dragon',
    );
    expect(
      library.root.querySelector('tbody tr:nth-child(2) td:last-child')?.textContent?.trim(),
    ).toBe('Non');
  });

  it('displays a column through its format, sorting on the raw value', async () => {
    ruleset.sections[1].columns[1].format = (value) => `<${String(value).length}>`;
    await open('/library/dnd5e-2014/monsters');
    const cells = [...library.root.querySelectorAll('tbody tr td:nth-child(2)')].map((cell) =>
      cell.textContent?.trim(),
    );
    expect(cells).toEqual(['<7>', '<0>']);
  });

  it('shows the placeholder, not the format, for a missing or null value', async () => {
    const format = vi.fn(() => 'formatted');
    ruleset.sections[1].columns[1].format = format;
    const entries: EntrySummary[] = [
      { index: 'a', name: 'A', fields: { tags: null as never } },
      { index: 'b', name: 'B', fields: {} },
    ];
    vi.spyOn(ruleset, 'list').mockReturnValue(of(entries));
    await open('/library/dnd5e-2014/monsters');
    const cells = [...library.root.querySelectorAll('tbody tr td:nth-child(2)')].map((cell) =>
      cell.textContent?.trim(),
    );
    expect(cells).toEqual(['', '']);
    expect(format).not.toHaveBeenCalled();
  });

  it('list filters by name ignoring accents and case', async () => {
    await open('/library/dnd5e-2014/spells?q=ECLAIR');
    expect(names()).toEqual(['Éclair']);
    expect(search().value).toBe('ECLAIR');

    await change(search(), 'fIre');
    expect(names()).toEqual(['Fireball']);
    expect(query()['q']).toBe('fIre');

    await change(search(), 'zzz');
    expect(names()).toEqual([]);
    expect(library.root.textContent).toContain('Aucune entrée ne correspond.');

    await change(search(), '');
    expect(names().length).toBe(4);
    expect(query()['q']).toBeUndefined();
  });

  describe('filter panel', () => {
    it('is closed until its button opens it, and says how many boxes are ticked', async () => {
      await open('/library/dnd5e-2014/spells?school=Abjuration&level=1');
      const panel = library.root.querySelector('aside.panel')!;
      expect(toggle().getAttribute('aria-expanded')).toBe('false');
      expect(toggle().getAttribute('aria-controls')).toBe(panel.id);
      expect(panel.classList).not.toContain('panel--open');
      expect(panel.querySelector('fieldset')).toBeNull();
      expect(
        library.root.querySelector('.table__filters-toggle .icon-button__badge')?.textContent,
      ).toBe('2');

      await openFilters();
      expect(toggle().getAttribute('aria-expanded')).toBe('true');
      expect(panel.classList).toContain('panel--open');
      expect(panel.getAttribute('aria-labelledby')).toBe(panel.querySelector('h2')?.id);
      expect([...panel.querySelectorAll('legend')].map((l) => l.textContent?.trim())).toEqual([
        'Niveau',
        'École',
        'Colonnes',
      ]);
    });

    it('closes with its button, the veil or Escape, and gives the focus back', async () => {
      await open('/library/dnd5e-2014/spells');
      await openFilters();
      library.root.querySelector<HTMLButtonElement>('.panel__head button')!.click();
      await settle();
      expect(toggle().getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(toggle());

      await openFilters();
      library.root.querySelector<HTMLElement>('.panel__veil')!.click();
      await settle();
      expect(toggle().getAttribute('aria-expanded')).toBe('false');

      await openFilters();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      await settle();
      expect(toggle().getAttribute('aria-expanded')).toBe('false');
      expect(library.root.querySelector('.panel__veil')).toBeNull();
    });

    it('shows the entries matching any box ticked in a filter, and every filter used', async () => {
      await open('/library/dnd5e-2014/spells');
      await openFilters();
      expect(choices('École')).toEqual(['Évocation', 'Abjuration']);

      await tick('École', 'Abjuration');
      expect(names()).toEqual(['Shield']);
      expect(query()['school']).toBe('Abjuration');

      await tick('École', 'Évocation');
      expect(names()).toEqual(['Light', 'Shield', 'Éclair', 'Fireball']);
      expect(query()['school']).toEqual(['Abjuration', 'Evocation']);
      expect(box('École', 'Abjuration').checked).toBe(true);

      await tick('Niveau', 'Niveau 3');
      // Same level: alphabetical order, accents ignored.
      expect(names()).toEqual(['Éclair', 'Fireball']);

      await tick('École', 'Abjuration');
      expect(query()['school']).toBe('Evocation');
      expect(box('École', 'Abjuration').checked).toBe(false);
    });

    it('clears every filter at once, keeping the search', async () => {
      await open('/library/dnd5e-2014/spells?school=Abjuration&level=1&q=s');
      await openFilters();
      library.root.querySelector<HTMLButtonElement>('.panel__clear')!.click();
      await settle();
      expect(query()).toEqual({ q: 's' });
      expect(library.root.querySelector('.panel__clear')).toBeNull();
    });

    it('ignores a filter value of the url that is not one of its options', async () => {
      await open('/library/dnd5e-2014/spells?school=xyz&level=03');
      expect(names().length).toBe(4);
      expect(library.root.querySelector('.icon-button__badge')).toBeNull();
      await openFilters();
      expect([...group('École').querySelectorAll('input')].some((input) => input.checked)).toBe(
        false,
      );
    });

    it('applies a filter to a list of values', async () => {
      ruleset.sections[1].filters.push({
        key: 'tags',
        label: 'Mot-clé',
        options: [{ value: 'old', label: 'Ancien' }],
      });
      await open('/library/dnd5e-2014/monsters?tags=old');
      expect(names()).toEqual(['Dragon']);
    });

    it('offers the values the entries have when a filter lists none', async () => {
      ruleset.sections[1].filters.push({ key: 'tags', label: 'Mot-clé' });
      await open('/library/dnd5e-2014/monsters?tags=old&tags=nope');
      expect(names()).toEqual(['Dragon']);
      await openFilters();
      expect(choices('Mot-clé')).toEqual(['big', 'old']);
      expect(box('Mot-clé', 'old').checked).toBe(true);
    });

    it('lets the reader hide columns but the first, remembered for the section', async () => {
      await open('/library/dnd5e-2014/monsters');
      await openFilters();
      expect(box('Colonnes', 'Nom').disabled).toBe(true);
      expect(box('Colonnes', 'Nom').checked).toBe(true);

      await tick('Colonnes', 'Mots-clés');
      expect(headers()).toEqual(['Nom', 'Légendaire']);
      expect(localStorage.getItem('dg.columns.dnd5e-2014.monsters')).toBe('["tags"]');

      await library.go('/library/dnd5e-2014/spells');
      expect(headers()).toEqual(['Nom', 'Niveau', 'École']);
      await library.go('/library/dnd5e-2014/monsters');
      expect(headers()).toEqual(['Nom', 'Légendaire']);

      await openFilters();
      await tick('Colonnes', 'Mots-clés');
      expect(headers()).toEqual(['Nom', 'Mots-clés', 'Légendaire']);
      expect(localStorage.getItem('dg.columns.dnd5e-2014.monsters')).toBeNull();
    });

    it('shows the first column even when the remembered choice hides it', async () => {
      library = await setupLibrary({ rulesets: [ruleset] });
      localStorage.setItem('dg.columns.dnd5e-2014.monsters', '["name","tags"]');
      await library.go('/library/dnd5e-2014/monsters');
      expect(headers()).toEqual(['Nom', 'Légendaire']);
    });

    it('shows every column when the remembered choice cannot be read', async () => {
      library = await setupLibrary({ rulesets: [ruleset] });
      localStorage.setItem('dg.columns.dnd5e-2014.monsters', '{not json');
      await library.go('/library/dnd5e-2014/monsters');
      expect(headers()).toEqual(['Nom', 'Mots-clés', 'Légendaire']);
    });
  });

  it('sorts a list by name until the reader picks a column', async () => {
    vi.spyOn(ruleset, 'list').mockReturnValue(
      of([
        { index: 'goblin', name: 'Goblin', fields: {} },
        { index: 'ogre', name: 'ogre', fields: {} },
        { index: 'aboleth', name: 'Âboleth', fields: {} },
      ]),
    );
    await open('/library/dnd5e-2014/monsters');
    expect(names()).toEqual(['Âboleth', 'Goblin', 'ogre']);
    expect(library.root.querySelector('thead th')?.getAttribute('aria-sort')).toBe('ascending');
    expect(query()['sort']).toBeUndefined();
  });

  it('sorts spells by level then by name until the reader picks a column', async () => {
    await open('/library/dnd5e-2014/spells');
    expect(names()).toEqual(['Light', 'Shield', 'Éclair', 'Fireball']);
    const [name, level] = [...library.root.querySelectorAll('thead th')];
    expect(level.getAttribute('aria-sort')).toBe('ascending');
    expect(name.getAttribute('aria-sort')).toBe('none');
  });

  it('list sorts by a column both ways, ties staying alphabetical', async () => {
    await open('/library/dnd5e-2014/spells');
    let header = await sortBy('Niveau');
    expect(names()).toEqual(['Éclair', 'Fireball', 'Shield', 'Light']);
    expect(header.getAttribute('aria-sort')).toBe('descending');
    expect(query()).toMatchObject({ sort: 'level', dir: 'desc' });

    header = await sortBy('Niveau');
    expect(names()).toEqual(['Light', 'Shield', 'Éclair', 'Fireball']);
    expect(header.getAttribute('aria-sort')).toBe('ascending');
    expect(query()).toMatchObject({ sort: 'level', dir: 'asc' });

    header = await sortBy('Nom');
    expect(names()).toEqual(['Éclair', 'Fireball', 'Light', 'Shield']);
    expect(header.getAttribute('aria-sort')).toBe('ascending');

    header = await sortBy('Nom');
    expect(names()).toEqual(['Shield', 'Light', 'Fireball', 'Éclair']);
    expect(header.getAttribute('aria-sort')).toBe('descending');

    const school = [...library.root.querySelectorAll('thead th')][2];
    expect(school.querySelector('button')).toBeNull();
    expect(school.hasAttribute('aria-sort')).toBe(false);
  });

  describe('with tabs', () => {
    const tabs = () => [...library.root.querySelectorAll<HTMLAnchorElement>('.list__tab')];
    const current = () => library.root.querySelector('.list__tab[aria-current="page"]');

    beforeEach(() => {
      ruleset.sections[2].tabs = {
        key: 'kind',
        label: 'Type',
        options: [
          { value: 'Alignment', label: 'Alignements' },
          { value: 'Language', label: 'Langues' },
        ],
      };
      vi.spyOn(ruleset, 'list').mockReturnValue(
        of([
          { index: 'language.elvish', name: 'Elvish', fields: { kind: 'Language' } },
          { index: 'alignment.neutral', name: 'Neutral', fields: { kind: 'Alignment' } },
          { index: 'language.common', name: 'Common', fields: { kind: 'Language' } },
          { index: 'alignment.chaotic', name: 'Chaotic Evil', fields: { kind: 'Alignment' } },
        ]),
      );
    });

    it('shows the first tab by default and only its entries', async () => {
      await open('/library/dnd5e-2014/rules');
      expect(library.root.querySelector('nav.list__tabs')?.getAttribute('aria-label')).toBe('Type');
      expect(tabs().map((tab) => tab.textContent?.trim())).toEqual(['Alignements', 'Langues']);
      expect(current()?.textContent?.trim()).toBe('Alignements');
      expect(names()).toEqual(['Chaotic Evil', 'Neutral']);
      expect(library.root.querySelector('.table__count')?.textContent).toContain('2 entrées');
    });

    it('switches tab through the url, back to the first page, keeping the search', async () => {
      await open('/library/dnd5e-2014/rules?page=2&q=e');
      expect(tabs()[1].getAttribute('href')).toBe('/library/dnd5e-2014/rules?q=e&kind=Language');
      expect(tabs()[0].getAttribute('href')).toBe('/library/dnd5e-2014/rules?q=e');

      tabs()[1].click();
      await library.harness.fixture.whenStable();
      await library.render();
      expect(query()).toEqual({ q: 'e', kind: 'Language' });
      expect(current()?.textContent?.trim()).toBe('Langues');
      expect(names()).toEqual(['Elvish']);
    });

    it('falls back to the first tab for a value of the url that is not one', async () => {
      await open('/library/dnd5e-2014/rules?kind=Nope');
      expect(current()?.textContent?.trim()).toBe('Alignements');
      expect(names()).toEqual(['Chaotic Evil', 'Neutral']);
    });

    it('shows no tabs for a section without them', async () => {
      await open('/library/dnd5e-2014/spells');
      expect(library.root.querySelector('.list__tabs')).toBeNull();
    });
  });

  it('list paginates and keeps state in the url', async () => {
    const many: EntrySummary[] = Array.from({ length: 60 }, (_, i) => ({
      index: `e${i + 1}`,
      name: `Entry ${String(i + 1).padStart(2, '0')}`,
      fields: {},
    }));
    vi.spyOn(ruleset, 'list').mockReturnValue(of(many));
    await open('/library/dnd5e-2014/rules?page=2');
    expect(names().length).toBe(25);
    expect(names()[0]).toBe('Entry 26');
    expect(library.root.querySelector('dg-paginator')?.textContent).toContain('Page 2 sur 3');

    library.root.querySelector<HTMLButtonElement>('[aria-label="Page suivante"]')!.click();
    await library.harness.fixture.whenStable();
    await library.render();
    expect(names()[0]).toBe('Entry 51');
    expect(names().length).toBe(10);
    expect(query()['page']).toBe('3');

    await openFilters();
    await change(library.root.querySelector<HTMLSelectElement>('.panel__size select')!, '50');
    expect(query()).toMatchObject({ size: '50' });
    expect(query()['page']).toBeUndefined();
    expect(names().length).toBe(50);

    await change(search(), 'Entry 5');
    expect(names().length).toBe(10);
    expect(names()[0]).toBe('Entry 50');
    expect(library.root.querySelector('dg-paginator nav')).toBeNull();
  });

  it('shows the last page when the page in the url is past the end', async () => {
    await open('/library/dnd5e-2014/spells?page=9&size=abc');
    expect(names().length).toBe(4);
  });

  it('loads the list once per section, not on every search', async () => {
    const list = vi.spyOn(ruleset, 'list');
    await open('/library/dnd5e-2014/spells');
    await change(search(), 'fire');
    await sortBy('Nom');
    expect(list).toHaveBeenCalledTimes(1);

    await library.go('/library/dnd5e-2014/monsters');
    expect(list).toHaveBeenCalledTimes(2);
    expect(list).toHaveBeenLastCalledWith('monsters');
    expect(names()).toEqual(['Dragon', 'Goblin']);
  });

  it("shows a loader with the section's flavour while the list loads", async () => {
    ruleset.sections[0].loadingMessage = 'Demande en cours à Mystra…';
    vi.spyOn(ruleset, 'list').mockReturnValue(NEVER);
    await open('/library/dnd5e-2014/spells');
    expect(library.root.querySelector('main dg-loading dg-spinner')).not.toBeNull();
    expect(library.root.querySelector('main dg-loading')?.textContent).toContain(
      'Demande en cours à Mystra…',
    );
    expect(library.root.querySelector('table')).toBeNull();
  });

  it('shows the generic flavour for a section without one', async () => {
    vi.spyOn(ruleset, 'list').mockReturnValue(NEVER);
    await open('/library/dnd5e-2014/monsters');
    expect(library.root.querySelector('main dg-loading')?.textContent).toContain(
      'Sélection des livres…',
    );
  });

  it('shows a retry state when the ruleset fails', async () => {
    const list = vi.spyOn(ruleset, 'list').mockReturnValueOnce(throwError(() => new Error('down')));
    await open('/library/dnd5e-2014/spells');
    expect(library.root.querySelector('dg-shell')).not.toBeNull();
    expect(library.root.querySelector('[role="alert"]')?.textContent).toContain(
      'Impossible de charger',
    );
    const retry = [...library.root.querySelectorAll('main button')].find((button) =>
      button.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    await library.render();
    expect(list).toHaveBeenCalledTimes(2);
    expect(names().length).toBe(4);
    expect(library.root.querySelector('[role="alert"]')).toBeNull();
  });

  describe('spell book buttons', () => {
    let backend: HttpTestingController;
    const mine = (r: { method: string; url: string }) =>
      r.method === 'GET' && /\/spellbooks\/mine$/.test(r.url);
    const buttons = () => [
      ...library.root.querySelectorAll<HTMLButtonElement>('tbody .add-to-book__trigger'),
    ];

    async function openSignedIn(url: string): Promise<void> {
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting(), provideApi()],
      });
      library = await setupLibrary({ rulesets: [ruleset], signedIn: true });
      backend = TestBed.inject(HttpTestingController);
      await library.go(url);
    }

    afterEach(() => backend?.verify());

    it('puts one on each row of a spell list, reading the books once', async () => {
      await openSignedIn('/library/dnd5e-2014/spells');
      backend
        .expectOne(mine)
        .flush([{ id: 'b1', name: 'Arcane', ruleset: 'dnd5e-2014', spellList: ['light'] }]);
      await library.render();

      expect(buttons().length).toBe(4);
      expect(buttons().map((b) => b.getAttribute('aria-label'))).toEqual([
        'Ajouter Light au grimoire',
        'Ajouter Shield au grimoire',
        'Ajouter Éclair au grimoire',
        'Ajouter Fireball au grimoire',
      ]);
      // In a row the words never unfold: they would push the columns.
      expect(library.root.querySelector('tbody .add-to-book__label')).toBeNull();
      expect(library.root.querySelector('thead .table__actions')?.textContent?.trim()).toBe(
        'Grimoire',
      );

      buttons()[1].click();
      await library.render();
      const item = document.querySelector<HTMLElement>('[role="menuitemcheckbox"]')!;
      expect(item.getAttribute('aria-checked')).toBe('false');
      item.click();
      backend
        .expectOne((r) => r.method === 'POST' && /spellbooks\/b1\/spells$/.test(r.url))
        .flush({ id: 'b1', name: 'Arcane', ruleset: 'dnd5e-2014', spellList: ['light', 'shield'] });
      await library.render();

      buttons()[1].click();
      await library.render();
      expect(
        document.querySelector('[role="menuitemcheckbox"]')?.getAttribute('aria-checked'),
      ).toBe('true');
    });

    it('puts none on a section that is not the spells', async () => {
      await openSignedIn('/library/dnd5e-2014/monsters');
      backend.expectNone(mine);
      expect(library.root.querySelector('.table__actions')).toBeNull();
    });

    it('puts none for a reader who is signed out', async () => {
      await open('/library/dnd5e-2014/spells');
      expect(library.root.querySelector('.table__actions')).toBeNull();
    });
  });
});
