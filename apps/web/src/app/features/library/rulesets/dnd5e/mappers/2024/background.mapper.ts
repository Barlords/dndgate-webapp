import { EntryDetail } from '../../../ruleset.model';
import { ApiRef, joinNames, names } from '../common';

export interface ApiBackground {
  index: string;
  name: string;
  ability_scores: ApiRef[];
  feat: ApiRef & { note?: string };
  proficiencies: ApiRef[];
  equipment_options: { desc: string }[];
}

export function mapBackground(background: ApiBackground): EntryDetail {
  const feat = background.feat.note
    ? `${background.feat.name} (${background.feat.note})`
    : background.feat.name;
  return {
    index: background.index,
    name: background.name,
    tags: [],
    blocks: [
      {
        kind: 'stats',
        items: [
          { label: 'Ability scores', value: names(background.ability_scores).join(', ') },
          { label: 'Origin feat', value: feat },
          { label: 'Proficiencies', value: joinNames(background.proficiencies) },
        ],
      },
      {
        kind: 'text',
        title: 'Starting equipment',
        paragraphs: background.equipment_options.map((option) => option.desc),
      },
    ],
  };
}
