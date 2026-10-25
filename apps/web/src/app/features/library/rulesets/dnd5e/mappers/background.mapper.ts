import { DetailBlock, EntryDetail } from '../../ruleset.model';
import { ApiRef, EntryTexts, joinNames, SOURCE_TEXTS } from './common';

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

function stringTable(
  title: string,
  label: string,
  set: OptionSet<StringOption>,
  t: EntryTexts,
): DetailBlock {
  return {
    kind: 'table',
    title,
    columns: [t.number, label],
    rows: set.from.options.map((option, i) => [String(i + 1), option.string]),
  };
}

export function mapBackground(
  background: ApiBackground,
  t: EntryTexts = SOURCE_TEXTS,
): EntryDetail {
  const stats = [
    { label: t.skillProficiencies, value: joinNames(background.starting_proficiencies, t.none) },
    ...(background.starting_gold
      ? [
          {
            label: t.startingGold,
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
      { kind: 'stats', layout: 'rows', items: stats },
      {
        kind: 'entries',
        title: t.feature,
        items: [{ name: background.feature.name, text: background.feature.desc.join('\n\n') }],
      },
      stringTable(t.personalityTraits, t.personalityTrait, background.personality_traits, t),
      {
        kind: 'table',
        title: t.ideals,
        columns: [t.number, t.ideal, t.alignments],
        rows: background.ideals.from.options.map((option, i) => [
          String(i + 1),
          option.desc,
          joinNames(option.alignments, '—'),
        ]),
      },
      stringTable(t.bonds, t.bond, background.bonds, t),
      stringTable(t.flaws, t.flaw, background.flaws, t),
    ],
  };
}
