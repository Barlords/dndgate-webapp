import { EntryDetail, EntrySummary } from '../../ruleset.model';
import { ApiRef, names, ordinal } from './common';

/** One spell of the GraphQL list. */
export interface GqlSpell {
  index: string;
  name: string;
  level: number;
  school: { name: string };
  classes: ApiRef[];
  ritual: boolean;
  concentration: boolean;
}

export interface ApiSpell {
  index: string;
  name: string;
  desc: string[];
  higher_level?: string[];
  range: string;
  components: string[];
  material?: string;
  ritual: boolean;
  duration: string;
  concentration: boolean;
  casting_time: string;
  level: number;
  school: ApiRef;
  classes: ApiRef[];
}

export function mapSpellSummary(spell: GqlSpell): EntrySummary {
  return {
    index: spell.index,
    name: spell.name,
    fields: {
      level: spell.level,
      school: spell.school.name,
      classes: names(spell.classes),
      ritual: spell.ritual,
      concentration: spell.concentration,
    },
  };
}

export function mapSpell(spell: ApiSpell): EntryDetail {
  const school = spell.school.name;
  const yesNo = (flag: boolean) => (flag ? 'Yes' : 'No');
  const blocks: EntryDetail['blocks'] = [
    {
      kind: 'stats',
      items: [
        { label: 'Casting time', value: spell.casting_time },
        { label: 'Range', value: spell.range },
        { label: 'Components', value: spell.components.join(', ') },
        { label: 'Duration', value: spell.duration },
        { label: 'Concentration', value: yesNo(spell.concentration) },
        { label: 'Ritual', value: yesNo(spell.ritual) },
      ],
    },
    { kind: 'text', title: 'Description', paragraphs: spell.desc },
  ];
  if (spell.higher_level && spell.higher_level.length > 0) {
    blocks.push({ kind: 'text', title: 'At higher levels', paragraphs: spell.higher_level });
  }
  if (spell.material) {
    blocks.push({ kind: 'text', title: 'Materials', paragraphs: [spell.material] });
  }
  return {
    index: spell.index,
    name: spell.name,
    subtitle:
      spell.level === 0
        ? `${school} cantrip`
        : `${ordinal(spell.level)}-level ${school.toLowerCase()}`,
    tags: names(spell.classes),
    blocks,
  };
}
