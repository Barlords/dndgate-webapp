import { DetailBlock, EntryDetail } from '../../ruleset.model';
import { ApiRef, joinNames } from './common';

interface StringOption {
  option_type: 'string';
  string: string;
}
interface IdealOption {
  option_type: 'ideal';
  desc: string;
  alignments: ApiRef[];
}

interface OptionSet<T> {
  choose: number;
  from: { options: T[] };
}

export interface ApiBackground {
  index: string;
  name: string;
  starting_proficiencies: ApiRef[];
  feature: { name: string; desc: string[] };
  personality_traits: OptionSet<StringOption>;
  ideals: OptionSet<IdealOption>;
  bonds: OptionSet<StringOption>;
  flaws: OptionSet<StringOption>;
  starting_gold?: { quantity: number; unit: string };
}

function stringTable(title: string, label: string, set: OptionSet<StringOption>): DetailBlock {
  return {
    kind: 'table',
    title,
    columns: ['No.', label],
    rows: set.from.options.map((option, i) => [String(i + 1), option.string]),
  };
}

export function mapBackground(background: ApiBackground): EntryDetail {
  const stats = [
    { label: 'Skill proficiencies', value: joinNames(background.starting_proficiencies) },
    ...(background.starting_gold
      ? [
          {
            label: 'Starting gold',
            value: `${background.starting_gold.quantity} ${background.starting_gold.unit}`,
          },
        ]
      : []),
  ];
  return {
    index: background.index,
    name: background.name,
    tags: [],
    blocks: [
      { kind: 'stats', items: stats },
      {
        kind: 'entries',
        title: 'Feature',
        items: [{ name: background.feature.name, text: background.feature.desc.join('\n\n') }],
      },
      stringTable('Personality traits', 'Personality trait', background.personality_traits),
      {
        kind: 'table',
        title: 'Ideals',
        columns: ['No.', 'Ideal', 'Alignments'],
        rows: background.ideals.from.options.map((option, i) => [
          String(i + 1),
          option.desc,
          joinNames(option.alignments, '—'),
        ]),
      },
      stringTable('Bonds', 'Bond', background.bonds),
      stringTable('Flaws', 'Flaw', background.flaws),
    ],
  };
}
