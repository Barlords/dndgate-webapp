import { FakeRuleset } from './rulesets/fake.ruleset';
import { LibraryTestbed, setupLibrary } from './library.testing';

describe('LibraryShell', () => {
  const classic = () => new FakeRuleset({ id: 'dnd5e-2014', label: 'Jeu classique' });
  const other = () => new FakeRuleset({ id: 'other-game', label: 'Autre jeu', spells: false });

  const groups = (root: HTMLElement) =>
    [...root.querySelectorAll('.sidebar__group')].map((group) => group.textContent?.trim());
  const select = (root: HTMLElement) =>
    root.querySelector<HTMLSelectElement>('dg-ruleset-select select')!;

  async function choose(library: LibraryTestbed, id: string): Promise<void> {
    const field = select(library.root);
    field.value = id;
    field.dispatchEvent(new Event('change'));
    await library.harness.fixture.whenStable();
    await library.render();
  }

  it('shows the library heading, the full ruleset names and the groups of its sections', async () => {
    const library = await setupLibrary({ rulesets: [classic(), other()] });
    await library.go('/library/dnd5e-2014');
    expect(library.root.querySelector('.sidebar__title')?.textContent).toBe('BIBLIOTHÈQUE');
    const options = [...select(library.root).options].map((option) => option.textContent?.trim());
    expect(options).toEqual(['Jeu classique', 'Autre jeu']);
    expect(select(library.root).value).toBe('dnd5e-2014');
    expect(groups(library.root)).toEqual(['POUR LES JOUEURS', 'POUR LE MJ', 'RÈGLES']);
    const links = [...library.root.querySelectorAll<HTMLAnchorElement>('.sidebar__link')];
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/library/dnd5e-2014/spells',
      '/library/dnd5e-2014/monsters',
      '/library/dnd5e-2014/rules',
    ]);
  });

  it('sidebar hides spell books when logged out or without spells', async () => {
    const library = await setupLibrary({ rulesets: [classic(), other()] });
    await library.go('/library/dnd5e-2014');
    expect(groups(library.root)).not.toContain('MES GRIMOIRES');

    library.account.set({ id: '1', pseudo: 'Me' });
    await library.render();
    expect(groups(library.root)).toContain('MES GRIMOIRES');
    expect(library.root.querySelector('a[href="/library/grimoires"]')?.textContent).toContain(
      'Mes grimoires',
    );

    await library.go('/library/other-game');
    expect(groups(library.root)).toEqual(['POUR LE MJ']);
  });

  it('changing ruleset keeps the section when it exists', async () => {
    const library = await setupLibrary({ rulesets: [classic(), other()] });
    await library.go('/library/dnd5e-2014/monsters');
    await choose(library, 'other-game');
    expect(library.router.url).toBe('/library/other-game/monsters');

    await choose(library, 'dnd5e-2014');
    await library.go('/library/dnd5e-2014/spells');
    await choose(library, 'other-game');
    expect(library.router.url).toBe('/library/other-game');
  });

  it('keeps the ruleset choice in the folded sidebar, as an icon opening a menu', async () => {
    const library = await setupLibrary({ rulesets: [classic(), other()] });
    await library.go('/library/dnd5e-2014/monsters');
    library.root.querySelector<HTMLButtonElement>('.sidebar__toggle')!.click();
    await library.render();

    expect(select(library.root)).toBeNull();
    const icon = library.root.querySelector<HTMLButtonElement>('.ruleset-select__icon')!;
    expect(icon.getAttribute('aria-label')).toBe('Jeu de règles : Jeu classique');
    icon.click();
    await library.render();
    const items = [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')];
    expect(items.map((item) => item.textContent?.replace('✓', '').trim())).toEqual([
      'Jeu classique',
      'Autre jeu',
    ]);
    expect(items[0].getAttribute('aria-checked')).toBe('true');

    items[1].click();
    await library.harness.fixture.whenStable();
    await library.render();
    expect(library.router.url).toBe('/library/other-game/monsters');
  });

  it('redirects the library root to the remembered ruleset, else to dnd5e-2014', async () => {
    const library = await setupLibrary({ rulesets: [other(), classic()] });
    await library.go('/library');
    expect(library.router.url).toBe('/library/dnd5e-2014');

    await library.go('/library/other-game');
    await library.go('/library');
    expect(library.router.url).toBe('/library/other-game');
  });

  it('shows the lost page for an unknown ruleset or section', async () => {
    const library = await setupLibrary();
    await library.go('/library/gone-game');
    expect(library.root.querySelector('dg-not-found-page')).not.toBeNull();
    expect(library.root.querySelector('dg-shell')).toBeNull();

    await library.go('/library/dnd5e-2014/gone-section');
    expect(library.root.querySelector('dg-not-found-page')).not.toBeNull();

    await library.go('/library/dnd5e-2014/spells/fireball/extra');
    expect(library.root.querySelector('dg-not-found-page')).not.toBeNull();
  });

  it('keeps spell books for signed-in readers', async () => {
    const library = await setupLibrary();
    await library.go('/library/grimoires');
    expect(library.router.url).toBe('/login?returnUrl=%2Flibrary%2Fgrimoires');

    library.account.set({ id: '1', pseudo: 'Me' });
    await library.go('/library/grimoires/42');
    expect(library.router.url).toBe('/library/grimoires/42');
    expect(library.root.querySelector('dg-shell')).not.toBeNull();
  });

  it('home of a ruleset links to its sections', async () => {
    const library = await setupLibrary();
    await library.go('/library/dnd5e-2014');
    const main = library.root.querySelector('main')!;
    expect(main.querySelector('h1')?.textContent).toContain('Jeu factice');
    const links = [...main.querySelectorAll<HTMLAnchorElement>('.home__section')];
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/library/dnd5e-2014/spells',
      '/library/dnd5e-2014/monsters',
      '/library/dnd5e-2014/rules',
    ]);
  });
});
