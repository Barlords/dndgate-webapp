import acolyte from '../__fixtures__/2014/backgrounds-acolyte.json';
import { ApiBackground, mapBackground } from './background.mapper';

// The JSON module types `option_type` as a plain string.
const api = acolyte as unknown as ApiBackground;

describe('background mapper', () => {
  it('maps the acolyte: proficiencies, gold, feature and the four personality tables', () => {
    const detail = mapBackground(api);
    expect(detail.blocks[0]).toEqual({
      kind: 'stats',
      items: [
        { label: 'Skill proficiencies', value: 'Skill: Insight, Skill: Religion' },
        { label: 'Starting gold', value: '15 gp' },
      ],
    });
    expect(detail.blocks[1]).toMatchObject({
      kind: 'entries',
      items: [{ name: 'Shelter of the Faithful' }],
    });
    const tables = detail.blocks.flatMap((b) => (b.kind === 'table' ? [b] : []));
    expect(tables.map((t) => t.title)).toEqual(['Personality traits', 'Ideals', 'Bonds', 'Flaws']);
    expect(tables[0].rows).toHaveLength(8);
    expect(tables[0].rows[0][0]).toBe('1');
    expect(tables[1].rows[0]).toEqual([
      '1',
      'Tradition. The ancient traditions of worship and sacrifice must be preserved and upheld.',
      'Lawful Good, Lawful Neutral, Lawful Evil',
    ]);
  });

  it('omits the gold line when the background has none', () => {
    const detail = mapBackground({ ...api, starting_gold: undefined });
    expect(JSON.stringify(detail.blocks[0])).not.toContain('gold');
  });
});
