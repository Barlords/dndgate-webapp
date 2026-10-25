import { DetailBlock, EntryDetail } from '../../ruleset.model';
import { ApiRef, EntryTexts, joinNames, names, signed, SOURCE_TEXTS } from './common';

export interface ApiRace {
  index: string;
  name: string;
  speed: number;
  ability_bonuses: { ability_score: ApiRef; bonus: number }[];
  alignment: string;
  age: string;
  size: string;
  size_description: string;
  languages: ApiRef[];
  language_desc: string;
  traits: ApiRef[];
  subraces?: ApiRef[];
}

export interface ApiTrait {
  index: string;
  name: string;
  desc: string[];
}

export function mapRace(
  race: ApiRace,
  traits: ApiTrait[],
  t: EntryTexts = SOURCE_TEXTS,
): EntryDetail {
  const bonuses = race.ability_bonuses
    .map((bonus) => `${bonus.ability_score.name} ${signed(bonus.bonus)}`)
    .join(', ');
  const blocks: DetailBlock[] = [
    {
      kind: 'stats',
      layout: 'rows',
      items: [
        { label: t.abilityScoreIncrease, value: bonuses || t.none },
        { label: t.size, value: race.size },
        { label: t.speed, value: t.feet(race.speed) },
        { label: t.languages, value: joinNames(race.languages, t.none) },
      ],
    },
    { kind: 'text', title: t.age, paragraphs: [race.age] },
    { kind: 'text', title: t.alignment, paragraphs: [race.alignment] },
    { kind: 'text', title: t.size, paragraphs: [race.size_description] },
    { kind: 'text', title: t.languages, paragraphs: [race.language_desc] },
  ];
  if (traits.length > 0) {
    blocks.push({
      kind: 'entries',
      title: t.traits,
      items: traits.map((trait) => ({ name: trait.name, text: trait.desc.join('\n\n') })),
    });
  }
  if (race.subraces && race.subraces.length > 0) {
    blocks.push({
      kind: 'text',
      title: t.subraces,
      paragraphs: [names(race.subraces).join(', ')],
    });
  }
  return {
    index: race.index,
    name: race.name,
    subtitle: t.humanoid(race.size),
    tags: [],
    blocks,
  };
}
