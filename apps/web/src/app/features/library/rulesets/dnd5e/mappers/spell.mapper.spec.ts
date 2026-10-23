import list from '../__fixtures__/2014/gql-spells.json';
import acidArrow from '../__fixtures__/2014/spells-acid-arrow.json';
import fireball from '../__fixtures__/2014/spells-fireball.json';
import { mapSpell, mapSpellSummary } from './spell.mapper';

describe('spell mapper', () => {
  it('maps a list entry to level, school, classes, ritual and concentration', () => {
    const summaries = list.data.spells.map(mapSpellSummary);
    expect(summaries[0]).toEqual({
      index: 'acid-splash',
      name: 'Acid Splash',
      fields: {
        level: 0,
        school: 'Conjuration',
        classes: ['Sorcerer', 'Wizard'],
        ritual: false,
        concentration: false,
      },
    });
    expect(summaries.find((s) => s.index === 'detect-magic')?.fields).toMatchObject({
      ritual: true,
      concentration: true,
    });
  });

  it('maps fireball: level 3 evocation, V S M, 8d6 in text', () => {
    const detail = mapSpell(fireball);
    expect(detail.subtitle).toBe('3rd-level evocation');
    expect(detail.tags).toEqual(['Sorcerer', 'Wizard']);
    expect(detail.blocks[0]).toEqual({
      kind: 'stats',
      items: [
        { label: 'Casting time', value: '1 action' },
        { label: 'Range', value: '150 feet' },
        { label: 'Components', value: 'V, S, M' },
        { label: 'Duration', value: 'Instantaneous' },
        { label: 'Concentration', value: 'No' },
        { label: 'Ritual', value: 'No' },
      ],
    });
    const texts = detail.blocks.flatMap((b) => (b.kind === 'text' ? [b] : []));
    expect(texts.map((b) => b.title)).toEqual(['Description', 'At higher levels', 'Materials']);
    expect(texts[0].paragraphs.join(' ')).toContain('8d6');
    expect(texts[2].paragraphs).toEqual(['A tiny ball of bat guano and sulfur.']);
  });

  it('calls a level 0 spell a cantrip and leaves out absent sections', () => {
    const detail = mapSpell({ ...acidArrow, level: 0, higher_level: [], material: undefined });
    expect(detail.subtitle).toBe('Evocation cantrip');
    expect(detail.blocks.map((b) => (b.kind === 'text' ? b.title : b.kind))).toEqual([
      'stats',
      'Description',
    ]);
  });

  it('uses the right ordinal suffixes', () => {
    const subtitle = (level: number) => mapSpell({ ...fireball, level }).subtitle;
    expect(subtitle(1)).toBe('1st-level evocation');
    expect(subtitle(2)).toBe('2nd-level evocation');
    expect(subtitle(9)).toBe('9th-level evocation');
  });
});
