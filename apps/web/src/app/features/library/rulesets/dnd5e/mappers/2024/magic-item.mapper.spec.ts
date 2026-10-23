import bag from '../../__fixtures__/2024/magic-items-bag-of-holding.json';
import thrower from '../../__fixtures__/2024/magic-items-dwarven-thrower.json';
import { ApiMagicItem, mapMagicItem } from './magic-item.mapper';

describe('magic item mapper (2024)', () => {
  it('maps category, rarity and description', () => {
    const detail = mapMagicItem(bag as ApiMagicItem);
    expect(detail.subtitle).toBe('Wondrous Items, Uncommon');
    expect(detail.tags).toEqual([]);
    expect(detail.blocks[0]).toEqual({
      kind: 'stats',
      items: [
        { label: 'Category', value: 'Wondrous Items' },
        { label: 'Rarity', value: 'Uncommon' },
        { label: 'Attunement', value: 'Not required' },
      ],
    });
    const text = detail.blocks[1];
    expect(text.kind === 'text' && text.paragraphs).toHaveLength(bag.desc.length);
  });

  it('says who may attune, and lists variants', () => {
    const detail = mapMagicItem({
      ...thrower,
      variants: [{ index: 'a', name: 'Variant A' }],
    } as ApiMagicItem);
    expect(detail.tags).toEqual(['Requires attunement']);
    const stats = detail.blocks[0];
    expect(stats.kind === 'stats' && stats.items.at(-1)).toEqual({
      label: 'Attunement',
      value: 'Required (Dwarf or a Creature Attuned to a Belt of Dwarvenkind)',
    });
    expect(detail.blocks.at(-1)).toEqual({
      kind: 'text',
      title: 'Variants',
      paragraphs: ['Variant A'],
    });
  });
});
