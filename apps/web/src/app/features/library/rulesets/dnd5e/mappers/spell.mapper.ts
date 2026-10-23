import { EntryDetail, EntrySummary } from '../../ruleset.model';
import { ApiRef, names, ordinal, paragraphs } from './common';

/** One spell of the GraphQL list. */
export interface GqlSpell {
  index: string;
  name: string;
  level: number;
  school: { name: string };
  classes: ApiRef[];
  ritual: boolean;
  concentration: boolean;
  components: string[];
  casting_time: string;
}

export interface ApiSpell {
  index: string;
  name: string;
  /** SRD 2014: a list of paragraphs. */
  desc?: string[];
  /** SRD 2024: one string. */
  description?: string;
  /** A list in SRD 2014, a string in SRD 2024. */
  higher_level?: string[] | string;
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
      castingTime: shortCastingTime(spell.casting_time),
      components: spell.components,
      ritual: spell.ritual,
      concentration: spell.concentration,
    },
  };
}

/**
 * The casting time without its trigger: SRD 2024 writes "Reaction, which you take when you are
 * hit…", too long for a column and a filter, where "Reaction" says enough.
 */
export function shortCastingTime(castingTime: string): string {
  return castingTime.split(', which')[0];
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
    { kind: 'text', title: 'Description', paragraphs: paragraphs(spell.desc ?? spell.description) },
  ];
  const higher = paragraphs(spell.higher_level);
  if (higher.length > 0) {
    blocks.push({ kind: 'text', title: 'At higher levels', paragraphs: higher });
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
