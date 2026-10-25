import { DetailBlock, EntryDetail } from '../../../ruleset.model';
import { ApiRef, EntryTexts, names, paragraphs, SOURCE_TEXTS } from '../common';

export interface ApiSpecies {
  index: string;
  name: string;
  type: string;
  /** Absent when the player chooses the size (`size_options`). */
  size?: string;
  size_options?: { from: { options: { size: string }[] } };
  speed: number;
  /** A species without traits (the dragonborn) has none. */
  traits?: ApiRef[];
  subspecies?: ApiRef[];
}

export interface ApiSpeciesTrait {
  index: string;
  name: string;
  description: string;
}

function sizeOf(species: ApiSpecies, t: EntryTexts): string {
  return (
    species.size ??
    species.size_options?.from.options.map((option) => option.size).join(` ${t.or} `) ??
    '—'
  );
}

export function mapSpecies(
  species: ApiSpecies,
  traits: ApiSpeciesTrait[],
  t: EntryTexts = SOURCE_TEXTS,
): EntryDetail {
  const size = sizeOf(species, t);
  const blocks: DetailBlock[] = [
    {
      kind: 'stats',
      layout: 'rows',
      items: [
        { label: t.size, value: size },
        { label: t.speed, value: t.feet(species.speed) },
      ],
    },
  ];
  if (traits.length > 0) {
    blocks.push({
      kind: 'entries',
      title: t.traits,
      items: traits.map((trait) => ({
        name: trait.name,
        text: paragraphs(trait.description).join('\n\n'),
      })),
    });
  }
  if (species.subspecies && species.subspecies.length > 0) {
    blocks.push({
      kind: 'text',
      title: t.subspecies,
      paragraphs: [names(species.subspecies).join(', ')],
    });
  }
  return {
    index: species.index,
    name: species.name,
    subtitle: t.creature(size, species.type),
    tags: [],
    blocks,
  };
}
