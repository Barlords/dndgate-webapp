import dragonborn from '../../__fixtures__/2024/species-dragonborn.json';
import tiefling from '../../__fixtures__/2024/species-tiefling.json';
import elf from '../../__fixtures__/2024/species-elf.json';
import { ApiSpecies, mapSpecies } from './species.mapper';

describe('species mapper (2024)', () => {
  it('maps size, speed, traits and subspecies', () => {
    const detail = mapSpecies(elf as ApiSpecies, [
      { index: 'trance', name: 'Trance', description: 'You do not need to sleep.\nYou meditate.' },
    ]);
    expect(detail.index).toBe('elf');
    expect(detail.subtitle).toBe('Medium humanoid');
    expect(detail.blocks[0]).toEqual({
      kind: 'stats',
      items: [
        { label: 'Size', value: 'Medium' },
        { label: 'Speed', value: '30 ft.' },
      ],
    });
    const traits = detail.blocks.find((b) => b.kind === 'entries');
    expect(traits).toEqual({
      kind: 'entries',
      title: 'Traits',
      items: [{ name: 'Trance', text: 'You do not need to sleep.\n\nYou meditate.' }],
    });
    expect(detail.blocks.at(-1)).toEqual({
      kind: 'text',
      title: 'Subspecies',
      paragraphs: ['Elven Lineage: Drow, Elven Lineage: High Elf, Elven Lineage: Wood Elf'],
    });
  });

  it('copes with a species that has no traits and lists its subspecies', () => {
    const detail = mapSpecies(dragonborn as ApiSpecies, []);
    expect(detail.blocks.some((b) => b.kind === 'entries')).toBe(false);
    expect(detail.blocks.some((b) => b.kind === 'text' && b.title === 'Subspecies')).toBe(true);
  });

  it('writes the sizes a species lets the player choose from', () => {
    const detail = mapSpecies(tiefling as ApiSpecies, []);
    expect(detail.subtitle).toBe('Small or Medium humanoid');
    expect(detail.blocks[0]).toMatchObject({
      items: [{ label: 'Size', value: 'Small or Medium' }, { label: 'Speed' }],
    });
    expect(mapSpecies({ ...tiefling, size_options: undefined } as ApiSpecies, []).subtitle).toBe(
      '— humanoid',
    );
  });

  it('leaves out an empty subspecies list', () => {
    const detail = mapSpecies({ ...elf, subspecies: [] } as ApiSpecies, []);
    expect(detail.blocks.some((b) => b.kind === 'text')).toBe(false);
  });
});
