import dwarf from '../__fixtures__/2014/races-dwarf.json';
import { mapRace } from './race.mapper';

describe('race mapper', () => {
  const traits = [
    { index: 'darkvision', name: 'Darkvision', desc: ['You can see in the dark.', 'Truly.'] },
  ];
  const titles = (blocks: ReturnType<typeof mapRace>['blocks']) =>
    blocks.map((b) => ('title' in b ? b.title : undefined));

  it('maps the dwarf: bonuses, speed, size, languages, traits and subraces', () => {
    const detail = mapRace(dwarf, traits);
    expect(detail.name).toBe('Dwarf');
    expect(detail.blocks[0]).toEqual({
      kind: 'stats',
      layout: 'rows',
      items: [
        { label: 'Ability score increase', value: 'CON +2' },
        { label: 'Size', value: 'Medium' },
        { label: 'Speed', value: '25 ft.' },
        { label: 'Languages', value: 'Common, Dwarvish' },
      ],
    });
    expect(titles(detail.blocks)).toEqual([
      undefined,
      'Age',
      'Alignment',
      'Size',
      'Languages',
      'Traits',
      'Subraces',
    ]);
    expect(detail.blocks[5]).toEqual({
      kind: 'entries',
      title: 'Traits',
      items: [{ name: 'Darkvision', text: 'You can see in the dark.\n\nTruly.' }],
    });
    expect(detail.blocks[6]).toMatchObject({ paragraphs: ['Hill Dwarf'] });
  });

  it('leaves out traits and subraces when there are none', () => {
    const detail = mapRace({ ...dwarf, subraces: [], ability_bonuses: [] }, []);
    expect(titles(detail.blocks)).toEqual([undefined, 'Age', 'Alignment', 'Size', 'Languages']);
    expect(JSON.stringify(detail.blocks[0])).toContain('None');
  });
});
