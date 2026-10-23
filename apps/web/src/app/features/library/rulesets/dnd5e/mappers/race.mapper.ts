import { DetailBlock, EntryDetail } from '../../ruleset.model';
import { ApiRef, joinNames, names, signed } from './common';

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

export function mapRace(race: ApiRace, traits: ApiTrait[]): EntryDetail {
  const bonuses = race.ability_bonuses
    .map((bonus) => `${bonus.ability_score.name} ${signed(bonus.bonus)}`)
    .join(', ');
  const blocks: DetailBlock[] = [
    {
      kind: 'stats',
      layout: 'rows',
      items: [
        { label: 'Ability score increase', value: bonuses || 'None' },
        { label: 'Size', value: race.size },
        { label: 'Speed', value: `${race.speed} ft.` },
        { label: 'Languages', value: joinNames(race.languages) },
      ],
    },
    { kind: 'text', title: 'Age', paragraphs: [race.age] },
    { kind: 'text', title: 'Alignment', paragraphs: [race.alignment] },
    { kind: 'text', title: 'Size', paragraphs: [race.size_description] },
    { kind: 'text', title: 'Languages', paragraphs: [race.language_desc] },
  ];
  if (traits.length > 0) {
    blocks.push({
      kind: 'entries',
      title: 'Traits',
      items: traits.map((trait) => ({ name: trait.name, text: trait.desc.join('\n\n') })),
    });
  }
  if (race.subraces && race.subraces.length > 0) {
    blocks.push({
      kind: 'text',
      title: 'Subraces',
      paragraphs: [names(race.subraces).join(', ')],
    });
  }
  return {
    index: race.index,
    name: race.name,
    subtitle: `${race.size} humanoid`,
    tags: [],
    blocks,
  };
}
