import { DetailBlock, EntryDetail } from '../../../ruleset.model';
import { ApiRef, names, paragraphs } from '../common';

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

function sizeOf(species: ApiSpecies): string {
  return (
    species.size ??
    species.size_options?.from.options.map((option) => option.size).join(' or ') ??
    '—'
  );
}

export function mapSpecies(species: ApiSpecies, traits: ApiSpeciesTrait[]): EntryDetail {
  const size = sizeOf(species);
  const blocks: DetailBlock[] = [
    {
      kind: 'stats',
      layout: 'rows',
      items: [
        { label: 'Size', value: size },
        { label: 'Speed', value: `${species.speed} ft.` },
      ],
    },
  ];
  if (traits.length > 0) {
    blocks.push({
      kind: 'entries',
      title: 'Traits',
      items: traits.map((trait) => ({
        name: trait.name,
        text: paragraphs(trait.description).join('\n\n'),
      })),
    });
  }
  if (species.subspecies && species.subspecies.length > 0) {
    blocks.push({
      kind: 'text',
      title: 'Subspecies',
      paragraphs: [names(species.subspecies).join(', ')],
    });
  }
  return {
    index: species.index,
    name: species.name,
    subtitle: `${size} ${species.type.toLowerCase()}`,
    tags: [],
    blocks,
  };
}
