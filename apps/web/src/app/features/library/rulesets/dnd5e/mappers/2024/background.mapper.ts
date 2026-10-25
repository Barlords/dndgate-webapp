import { EntryDetail } from '../../../ruleset.model';
import { ApiRef, EntryTexts, joinNames, names, SOURCE_TEXTS } from '../common';

export interface ApiBackground {
  index: string;
  name: string;
  ability_scores: ApiRef[];
  feat: ApiRef & { note?: string };
  proficiencies: ApiRef[];
  equipment_options: { desc: string }[];
}

export function mapBackground(
  background: ApiBackground,
  t: EntryTexts = SOURCE_TEXTS,
): EntryDetail {
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
        layout: 'rows',
        items: [
          { label: t.abilityScores, value: names(background.ability_scores).join(', ') },
          { label: t.originFeat, value: feat },
          { label: t.proficiencies, value: joinNames(background.proficiencies, t.none) },
        ],
      },
      {
        kind: 'text',
        title: t.startingEquipment,
        paragraphs: background.equipment_options.map((option) => option.desc),
      },
    ],
  };
}
