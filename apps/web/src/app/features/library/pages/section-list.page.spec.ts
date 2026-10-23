import { NEVER, of, throwError } from 'rxjs';
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
  const field = <T extends HTMLElement>(label: string) =>
    [...library.root.querySelectorAll('.list__filters label')]
      .find((caption) => caption.textContent?.includes(label))!
      .querySelector('input, select') as T;

  async function change(element: HTMLInputElement | HTMLSelectElement, value: string) {
    element.value = value;
    element.dispatchEvent(new Event(element instanceof HTMLInputElement ? 'input' : 'change'));
    await library.harness.fixture.whenStable();
    await library.render();
  }

  async function sortBy(label: string) {
    const header = [...library.root.querySelectorAll('thead th')].find((cell) =>
      cell.textContent?.includes(label),
    )!;
    header.querySelector('button')!.click();
    await library.harness.fixture.whenStable();
    await library.render();
    return header;
  }

  it('lists the entries with their columns and links them to their page', async () => {
    await open('/library/dnd5e-2014/monsters');
    expect(library.root.querySelector('h1')?.textContent).toContain('Monstres');
    const headers = [...library.root.querySelectorAll('thead th')].map((cell) =>
      cell.textContent?.trim(),
    );
    expect(headers).toEqual(['Nom', 'Mots-clés', 'Légendaire']);
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
    expect(field<HTMLInputElement>('Rechercher').value).toBe('ECLAIR');

    await change(field<HTMLInputElement>('Rechercher'), 'fIre');
    expect(names()).toEqual(['Fireball']);
    expect(query()['q']).toBe('fIre');

    await change(field<HTMLInputElement>('Rechercher'), 'zzz');
    expect(names()).toEqual([]);
    expect(library.root.textContent).toContain('Aucune entrée ne correspond.');

    await change(field<HTMLInputElement>('Rechercher'), '');
    expect(names().length).toBe(4);
    expect(query()['q']).toBeUndefined();
  });

  it('list applies a select filter', async () => {
    await open('/library/dnd5e-2014/spells');
    const school = field<HTMLSelectElement>('École');
    expect([...school.options].map((option) => option.textContent?.trim())).toEqual([
      'Tout',
      'Évocation',
      'Abjuration',
    ]);
    await change(school, 'Abjuration');
    expect(names()).toEqual(['Shield']);
    expect(query()['school']).toBe('Abjuration');

    await change(field<HTMLSelectElement>('École'), '');
    await change(field<HTMLSelectElement>('Niveau'), '3');
    expect(names()).toEqual(['Fireball', 'Éclair']);
    expect(query()['school']).toBeUndefined();
  });

  it('ignores a filter value of the url that is not one of its options', async () => {
    await open('/library/dnd5e-2014/spells?school=xyz&level=03');
    expect(names().length).toBe(4);
    for (const label of ['École', 'Niveau']) {
      const select = field<HTMLSelectElement>(label);
      expect(select.value).toBe('');
      expect(select.selectedOptions[0].textContent?.trim()).toBe('Tout');
    }
  });

  it('list applies a filter to a list of values', async () => {
    ruleset.sections[1].filters.push({
      key: 'tags',
      label: 'Mot-clé',
      options: [{ value: 'old', label: 'Ancien' }],
    });
    await open('/library/dnd5e-2014/monsters?tags=old');
    expect(names()).toEqual(['Dragon']);
  });

  it('list sorts by a column both ways', async () => {
    await open('/library/dnd5e-2014/spells');
    let header = await sortBy('Niveau');
    expect(names()).toEqual(['Light', 'Shield', 'Fireball', 'Éclair']);
    expect(header.getAttribute('aria-sort')).toBe('ascending');
    expect(query()).toMatchObject({ sort: 'level', dir: 'asc' });

    header = await sortBy('Niveau');
    expect(names()).toEqual(['Fireball', 'Éclair', 'Shield', 'Light']);
    expect(header.getAttribute('aria-sort')).toBe('descending');
    expect(query()).toMatchObject({ sort: 'level', dir: 'desc' });

    header = await sortBy('Nom');
    expect(names()).toEqual(['Éclair', 'Fireball', 'Light', 'Shield']);
    expect(header.getAttribute('aria-sort')).toBe('ascending');

    const school = [...library.root.querySelectorAll('thead th')][2];
    expect(school.querySelector('button')).toBeNull();
    expect(school.hasAttribute('aria-sort')).toBe(false);
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

    await change(field<HTMLSelectElement>('Par page'), '50');
    expect(query()).toMatchObject({ size: '50' });
    expect(query()['page']).toBeUndefined();
    expect(names().length).toBe(50);

    await change(field<HTMLInputElement>('Rechercher'), 'Entry 5');
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
    await change(field<HTMLInputElement>('Rechercher'), 'fire');
    await sortBy('Nom');
    expect(list).toHaveBeenCalledTimes(1);

    await library.go('/library/dnd5e-2014/monsters');
    expect(list).toHaveBeenCalledTimes(2);
    expect(list).toHaveBeenLastCalledWith('monsters');
    expect(names()).toEqual(['Dragon', 'Goblin']);
  });

  it('shows a spinner while the list loads', async () => {
    vi.spyOn(ruleset, 'list').mockReturnValue(NEVER);
    await open('/library/dnd5e-2014/spells');
    expect(library.root.querySelector('main dg-spinner')).not.toBeNull();
    expect(library.root.querySelector('table')).toBeNull();
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
});
