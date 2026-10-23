import alignment from '../__fixtures__/2014/alignments-lawful-good.json';
import language from '../__fixtures__/2014/languages-common.json';
import {
  mapAlignment,
  mapAlignmentSummary,
  mapLanguage,
  mapLanguageSummary,
} from './personality.mapper';

describe('personality mapper', () => {
  it('prefixes the index so that an alignment and a language never collide', () => {
    const ref = { index: 'common', name: 'Common' };
    expect(mapAlignmentSummary(ref)).toEqual({
      index: 'alignment.common',
      name: 'Common',
      fields: { kind: 'Alignment' },
    });
    expect(mapLanguageSummary(ref).index).toBe('language.common');
  });

  it('maps an alignment to its abbreviation and description', () => {
    const detail = mapAlignment(alignment);
    expect(detail).toMatchObject({ name: 'Lawful Good', tags: ['LG'], subtitle: 'Alignment' });
    expect(detail.blocks).toEqual([{ kind: 'text', paragraphs: [alignment.desc] }]);
  });

  it('maps a language to its type, script and speakers', () => {
    expect(mapLanguage(language).blocks[0]).toEqual({
      kind: 'stats',
      items: [
        { label: 'Type', value: 'Standard' },
        { label: 'Script', value: 'Common' },
        { label: 'Typical speakers', value: 'Humans' },
      ],
    });
  });

  it('shows dashes for a language without script or speakers', () => {
    const detail = mapLanguage({ ...language, script: undefined, typical_speakers: [] });
    expect(JSON.stringify(detail.blocks[0])).toContain('{"label":"Script","value":"—"}');
    expect(JSON.stringify(detail.blocks[0])).toContain('{"label":"Typical speakers","value":"—"}');
  });
});
