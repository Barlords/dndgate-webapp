import lawfulGood from '../../__fixtures__/2024/alignments-lawful-good.json';
import common from '../../__fixtures__/2024/languages-common.json';
import { ApiAlignment, ApiLanguage, mapAlignment, mapLanguage } from './personality.mapper';

describe('personality mapper (2024)', () => {
  it('maps an alignment from its description', () => {
    const detail = mapAlignment(lawfulGood as ApiAlignment);
    expect(detail).toMatchObject({
      index: 'alignment.lawful-good',
      subtitle: 'Alignment',
      tags: ['LG'],
    });
    const text = detail.blocks[0];
    expect(text.kind === 'text' && text.paragraphs[0]).toContain('right thing');
  });

  it('maps a language with its rarity and note', () => {
    const detail = mapLanguage(common as ApiLanguage);
    expect(detail.index).toBe('language.common');
    expect(detail.blocks[0]).toEqual({
      kind: 'stats',
      items: [
        { label: 'Rarity', value: 'Standard' },
        { label: 'Note', value: '—' },
      ],
    });
    const rare = mapLanguage({ ...common, is_rare: true, note: 'Spoken by few.' } as ApiLanguage);
    expect(rare.blocks[0]).toEqual({
      kind: 'stats',
      items: [
        { label: 'Rarity', value: 'Rare' },
        { label: 'Note', value: 'Spoken by few.' },
      ],
    });
  });
});
