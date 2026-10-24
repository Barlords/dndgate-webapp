import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import { LibraryTestbed, setupLibrary } from '../library.testing';
import { FAKE_FIREBALL, FakeRuleset } from '../rulesets/fake.ruleset';
import { EntryDetail, RulesetEntryNotFoundError } from '../rulesets/ruleset.model';

describe('EntryDetailPage', () => {
  let ruleset: FakeRuleset;
  let library: LibraryTestbed;

  async function open(url: string): Promise<void> {
    library = await setupLibrary({ rulesets: [ruleset] });
    await library.go(url);
  }

  const crumbs = () =>
    TestBed.inject(BreadcrumbService)
      .crumbs()
      .map((crumb) => crumb.label);

  beforeEach(() => {
    ruleset = new FakeRuleset();
  });

  it('shows the title, subtitle, tags and blocks of the entry', async () => {
    const detail: EntryDetail = {
      ...FAKE_FIREBALL,
      blocks: [
        { kind: 'stats', items: [{ label: 'Range', value: '150 feet' }] },
        { kind: 'text', paragraphs: ['A bright **streak**.'] },
        { kind: 'table', columns: ['Level', 'Damage'], rows: [['4', '9d6']] },
        { kind: 'entries', items: [{ name: 'Spark', text: 'Burns.' }] },
      ],
    };
    vi.spyOn(ruleset, 'detail').mockReturnValue(of(detail));
    await open('/library/dnd5e-2014/spells/fireball');
    const main = library.root.querySelector('main')!;
    expect(main.querySelector('h1')?.textContent?.trim()).toBe('Fireball');
    expect(main.querySelector('.entry__subtitle')?.textContent?.trim()).toBe('3rd-level evocation');
    expect([...main.querySelectorAll('.entry__tag')].map((tag) => tag.textContent?.trim())).toEqual(
      ['Wizard', 'Sorcerer'],
    );
    expect(main.querySelector('dl')?.textContent).toContain('150 feet');
    expect(main.querySelector('strong')?.textContent).toBe('streak');
    expect(main.querySelector('table td:last-child')?.textContent?.trim()).toBe('9d6');
    expect(main.textContent).toContain('Spark');
    expect(ruleset.detail).toHaveBeenCalledWith('spells', 'fireball');
  });

  it('says when the entry is not in the reader’s language', async () => {
    ruleset = new FakeRuleset({ untranslated: ['spells'] });
    await open('/library/dnd5e-2014/spells/fireball');
    expect(library.root.querySelector('dg-untranslated-note')?.textContent).toContain(
      'Pas encore traduit',
    );
  });

  it('says nothing about the language of a translated entry', async () => {
    await open('/library/dnd5e-2014/spells/fireball');
    expect(library.root.querySelector('h1')?.textContent).toContain('Fireball');
    expect(library.root.querySelector('dg-untranslated-note')).toBeNull();
  });

  it('detail sets the breadcrumb to the entry name', async () => {
    await open('/library/dnd5e-2014/spells/fireball');
    expect(crumbs()).toEqual(['Bibliothèque', 'Sorts', 'Fireball']);
    const trail = library.root.querySelector('nav[aria-label="Fil d\'Ariane"]')!;
    expect(trail.querySelector('[aria-current="page"]')?.textContent).toBe('Fireball');
    expect(trail.querySelector('a[href="/library/dnd5e-2014/spells"]')?.textContent).toBe('Sorts');
  });

  it('drops a response that arrives after leaving the page', async () => {
    const late = new Subject<EntryDetail>();
    vi.spyOn(ruleset, 'detail').mockReturnValue(late);
    ruleset.sections[0].loadingMessage = 'Demande en cours à Mystra…';
    await open('/library/dnd5e-2014/spells/fireball');
    expect(library.root.querySelector('main dg-loading')?.textContent).toContain(
      'Demande en cours à Mystra…',
    );

    await library.go('/library/dnd5e-2014/spells');
    expect(late.observed).toBe(false);
    late.next(FAKE_FIREBALL);
    expect(crumbs()).toEqual(['Bibliothèque', 'Sorts']);
  });

  it('shows a retry state when the entry cannot be loaded', async () => {
    const detail = vi
      .spyOn(ruleset, 'detail')
      .mockReturnValueOnce(throwError(() => new Error('down')));
    await open('/library/dnd5e-2014/spells/fireball');
    expect(library.root.querySelector('[role="alert"]')?.textContent).toContain(
      'Impossible de charger',
    );
    expect(library.root.querySelector('dg-shell')).not.toBeNull();
    const retry = [...library.root.querySelectorAll('main button')].find((button) =>
      button.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    await library.render();
    expect(detail).toHaveBeenCalledTimes(2);
    expect(library.root.querySelector('main h1')?.textContent?.trim()).toBe('Fireball');
  });

  it('shows "Entrée introuvable." with a link back and no retry for a missing entry', async () => {
    vi.spyOn(ruleset, 'detail').mockReturnValue(throwError(() => new RulesetEntryNotFoundError()));
    await open('/library/dnd5e-2014/spells/nope');
    const main = library.root.querySelector('main')!;
    expect(main.querySelector('[role="alert"]')?.textContent).toContain('Entrée introuvable.');
    expect(main.textContent).not.toContain('Réessayer');
    expect(main.textContent).not.toContain('Impossible de charger');
    expect(main.querySelector('a[href="/library/dnd5e-2014/spells"]')).not.toBeNull();
  });

  it('follows the index when the page is reused for another entry', async () => {
    await open('/library/dnd5e-2014/spells/fireball');
    await library.go('/library/dnd5e-2014/spells/light');
    expect(library.root.querySelector('main h1')).toBeNull();
    expect(library.root.querySelector('[role="alert"]')).not.toBeNull();
    expect(crumbs()).toEqual(['Bibliothèque', 'Sorts', 'light']);
  });
});
