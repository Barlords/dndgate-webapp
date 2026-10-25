import { EntryDetail, EntrySummary } from '../../ruleset.model';
import { ApiRef, EntryTexts, names, paragraphs, SOURCE_TEXTS } from './common';

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
 * hit…" (and the French SRD 2014 "1 réaction, que vous jouez…"), too long for a column and a
 * filter, where "Reaction" says enough. The trigger is what follows the first comma, in any
 * language: no casting time of the API has a comma otherwise.
 */
export function shortCastingTime(castingTime: string): string {
  return castingTime.split(',')[0].trim();
}

export function mapSpell(spell: ApiSpell, t: EntryTexts = SOURCE_TEXTS): EntryDetail {
  const school = spell.school.name;
  const yesNo = (flag: boolean) => (flag ? t.yes : t.no);
  const blocks: EntryDetail['blocks'] = [
    {
      kind: 'stats',
      items: [
        { label: t.castingTime, value: spell.casting_time },
        { label: t.range, value: spell.range },
        { label: t.components, value: spell.components.join(', ') },
        { label: t.duration, value: spell.duration },
        { label: t.concentration, value: yesNo(spell.concentration) },
        { label: t.ritual, value: yesNo(spell.ritual) },
      ],
    },
    { kind: 'text', title: t.description, paragraphs: paragraphs(spell.desc ?? spell.description) },
  ];
  const higher = paragraphs(spell.higher_level);
  if (higher.length > 0) {
    blocks.push({ kind: 'text', title: t.higherLevels, paragraphs: higher });
  }
  if (spell.material) {
    blocks.push({ kind: 'text', title: t.materials, paragraphs: [spell.material] });
  }
  return {
    index: spell.index,
    name: spell.name,
    subtitle: spell.level === 0 ? t.cantripOf(school) : t.spellOf(spell.level, school),
    tags: names(spell.classes),
    blocks,
  };
}
