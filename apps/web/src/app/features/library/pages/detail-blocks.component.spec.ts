import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DetailBlock } from '../rulesets/ruleset.model';
import { DetailBlocks } from './detail-blocks.component';

@Component({
  imports: [DetailBlocks],
  template: '<dg-detail-blocks rulesetId="some-game" [blocks]="blocks()" />',
})
class Host {
  readonly blocks = signal<DetailBlock[]>([]);
}

describe('DetailBlocks', () => {
  async function render(blocks: DetailBlock[]): Promise<HTMLElement> {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.blocks.set(blocks);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (paragraphs: string[]): DetailBlock[] => [{ kind: 'text', paragraphs }];
  const texts = (root: HTMLElement, selector: string) =>
    [...root.querySelectorAll(selector)].map((element) => element.textContent?.trim());

  it('detail renders text, stats, table and entries blocks', async () => {
    const root = await render([
      { kind: 'text', title: 'Description', paragraphs: ['First.', 'Second.'] },
      {
        kind: 'stats',
        title: 'Profil',
        items: [
          { label: 'Range', value: '150 feet' },
          { label: 'Duration', value: 'Instantaneous' },
        ],
      },
      {
        kind: 'table',
        title: 'Damage',
        columns: ['Level', 'Dice'],
        rows: [
          ['4', '9d6'],
          ['5', '10d6'],
        ],
      },
      {
        kind: 'entries',
        title: 'Actions',
        items: [
          { name: 'Bite', text: 'Ouch.' },
          { name: 'Goblin', text: 'A friend.', link: { section: 'monsters', index: 'goblin' } },
        ],
      },
    ]);
    expect(texts(root, 'h2')).toEqual(['Description', 'Profil', 'Damage', 'Actions']);
    expect(texts(root, '.block--text p')).toEqual(['First.', 'Second.']);
    expect(texts(root, 'dt')).toEqual(['Range', 'Duration']);
    expect(texts(root, 'dd')).toEqual(['150 feet', 'Instantaneous']);
    expect(texts(root, '.block--table th')).toEqual(['Level', 'Dice']);
    expect(texts(root, '.block--table tbody tr:last-child td')).toEqual(['5', '10d6']);
    expect(texts(root, '.block__entry-name')).toEqual(['Bite', 'Goblin']);
    expect(texts(root, '.block--entries p')).toEqual(['Ouch.', 'A friend.']);
    expect(root.querySelector('.block--entries a')?.getAttribute('href')).toBe(
      '/library/some-game/monsters/goblin',
    );
  });

  it('gives anchors to blocks and entries, and makes a nested title a sub-heading', async () => {
    const root = await render([
      { kind: 'text', title: 'Subclasses', anchor: 'subclasses', paragraphs: [] },
      { kind: 'text', title: 'Arcane Tradition: Evocation', nested: true, paragraphs: ['Booms.'] },
      {
        kind: 'entries',
        title: 'Class features',
        items: [{ name: 'Arcane Recovery', text: 'Rest.', anchor: 'feature-arcane-recovery' }],
      },
    ]);
    expect(root.querySelector('section#subclasses h2')?.textContent).toBe('Subclasses');
    expect(texts(root, 'h3.block__title--nested')).toEqual(['Arcane Tradition: Evocation']);
    expect(
      root.querySelector('#feature-arcane-recovery .block__entry-name')?.textContent,
    ).toContain('Arcane Recovery');
  });

  it('links the parts of a table cell to their anchor on the page', async () => {
    const root = await render([
      {
        kind: 'table',
        columns: ['Level', 'Features'],
        rows: [
          ['1', [{ text: 'Rage', anchor: 'feature-rage' }, { text: 'Unknown' }]],
          ['2', '—'],
        ],
      },
      { kind: 'entries', items: [{ name: 'Rage', text: 'Grr.', anchor: 'feature-rage' }] },
    ]);
    const target = root.querySelector<HTMLElement>('#feature-rage')!;
    const scroll = vi.fn();
    target.scrollIntoView = scroll;
    expect(texts(root, 'tbody tr:first-child td:last-child')).toEqual(['Rage, Unknown']);
    const link = root.querySelector<HTMLAnchorElement>('td a')!;
    expect(link.textContent).toBe('Rage');
    expect(link.getAttribute('href')).toBe('/#feature-rage');
    expect(root.querySelectorAll('td a').length).toBe(1);
    link.click();
    expect(scroll).toHaveBeenCalledWith({ behavior: 'smooth' });
  });

  it('puts each stat on a line of its own in the rows layout', async () => {
    const root = await render([
      { kind: 'stats', layout: 'rows', items: [{ label: 'Hit die', value: 'd6' }] },
      { kind: 'stats', items: [{ label: 'Range', value: '150 feet' }] },
    ]);
    const lists = [...root.querySelectorAll('dl')];
    expect(lists[0].classList).toContain('block__stats--rows');
    expect(lists[1].classList).not.toContain('block__stats--rows');
  });

  it('renders bold and italic text', async () => {
    const root = await render(text(['A **bold** and *italic* and ***both*** word.']));
    expect(root.querySelector('p')?.textContent).toBe('A bold and italic and both word.');
    expect(texts(root, 'strong')).toEqual(['bold', 'both']);
    expect(texts(root, 'em')).toEqual(['italic', 'both']);
  });

  it('keeps lone asterisks as they are', async () => {
    const root = await render(text(['5 * 3 is **not closed', 'a* b *c']));
    expect(texts(root, 'p')).toEqual(['5 * 3 is **not closed', 'a* b *c']);
    expect(root.querySelector('strong, em')).toBeNull();
  });

  it('renders headings, lists and paragraphs of one text', async () => {
    const root = await render(
      text([
        '## Casting\nSome text\non two lines.\n\n- one **1**\n* two\n### Details\nEnd.\n#### Deep',
      ]),
    );
    expect(texts(root, 'h3')).toEqual(['Casting']);
    expect(texts(root, 'h4')).toEqual(['Details', 'Deep']);
    expect(texts(root, 'p')).toEqual(['Some text on two lines.', 'End.']);
    expect(texts(root, 'li')).toEqual(['one 1', 'two']);
    expect(root.querySelector('li strong')?.textContent).toBe('1');
  });

  it('renders pipe tables with or without a header', async () => {
    const root = await render(
      text(['| Size | Space |\n|---|:---:|\n| Tiny | 2 *ft* |\n| Small | 5 ft |', '| a | b |']),
    );
    const [withHeader, without] = [...root.querySelectorAll('table')];
    expect(texts(withHeader, 'th')).toEqual(['Size', 'Space']);
    expect(texts(withHeader, 'tbody tr:first-child td')).toEqual(['Tiny', '2 ft']);
    expect(withHeader.querySelector('td em')?.textContent).toBe('ft');
    expect(without.querySelector('th')).toBeNull();
    expect(texts(without, 'td')).toEqual(['a', 'b']);
  });

  it('never interprets markup in the text', async () => {
    const root = await render([
      { kind: 'text', paragraphs: ['<img src="x" onerror="alert(1)"> **<b>hi</b>**'] },
      { kind: 'entries', items: [{ name: '<i>x</i>', text: '<script>alert(1)</script>' }] },
    ]);
    expect(root.querySelector('img, b, i, script')).toBeNull();
    expect(root.textContent).toContain('<img src="x" onerror="alert(1)">');
    expect(root.querySelector('strong')?.textContent).toBe('<b>hi</b>');
  });
});
