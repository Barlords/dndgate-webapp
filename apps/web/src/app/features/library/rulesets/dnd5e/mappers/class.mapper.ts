import { DetailBlock, EntryDetail } from '../../ruleset.model';
import { ApiRef, joinNames, names, ordinal } from './common';

export interface ApiClass {
  index: string;
  name: string;
  hit_die: number;
  proficiency_choices?: { desc: string }[];
  proficiencies: ApiRef[];
  saving_throws: ApiRef[];
  /** SRD 2024 has only the options. */
  starting_equipment?: { equipment: ApiRef; quantity: number }[];
  /** SRD 2024. */
  primary_ability?: { desc: string };
  starting_equipment_options?: { desc: string }[];
  subclasses?: ApiRef[];
  spellcasting?: { spellcasting_ability: ApiRef; info: { name: string; desc: string[] }[] };
}

export interface ApiClassLevel {
  level: number;
  prof_bonus: number;
  features: ApiRef[];
  spellcasting?: Record<string, number>;
}

const SLOT_LEVELS = [1, 2, 3, 4, 5, 6, 7, 8, 9];

/** The levels table: level, proficiency bonus, features, and the spellcasting columns in use. */
function levelsTable(levels: ApiClassLevel[]): DetailBlock[] {
  if (levels.length === 0) {
    return [];
  }
  const cantrips = levels.some((level) => level.spellcasting?.['cantrips_known'] !== undefined);
  const prepared = levels.some((level) => level.spellcasting?.['prepared_spells'] !== undefined);
  const slots = SLOT_LEVELS.filter((slot) =>
    levels.some((level) => (level.spellcasting?.[`spell_slots_level_${slot}`] ?? 0) > 0),
  );
  const columns = [
    'Level',
    'Proficiency bonus',
    'Features',
    ...(cantrips ? ['Cantrips'] : []),
    ...(prepared ? ['Prepared spells'] : []),
    ...slots.map((slot) => `${ordinal(slot)}`),
  ];
  const rows = levels.map((level) => [
    String(level.level),
    `+${level.prof_bonus}`,
    names(level.features).join(', ') || '—',
    ...(cantrips ? [String(level.spellcasting?.['cantrips_known'] ?? 0)] : []),
    ...(prepared ? [String(level.spellcasting?.['prepared_spells'] ?? 0)] : []),
    ...slots.map((slot) => String(level.spellcasting?.[`spell_slots_level_${slot}`] ?? 0)),
  ]);
  return [{ kind: 'table', title: 'Class levels', columns, rows }];
}

export function mapClass(cls: ApiClass, levels: ApiClassLevel[]): EntryDetail {
  const equipment = (cls.starting_equipment ?? []).map(
    (item) => `${item.equipment.name}${item.quantity > 1 ? ` x${item.quantity}` : ''}`,
  );
  const equipmentChoices = (cls.starting_equipment_options ?? []).map((option) => option.desc);
  const stats = [
    { label: 'Hit die', value: `d${cls.hit_die}` },
    ...(cls.primary_ability ? [{ label: 'Primary ability', value: cls.primary_ability.desc }] : []),
    { label: 'Saving throws', value: joinNames(cls.saving_throws) },
    { label: 'Proficiencies', value: joinNames(cls.proficiencies) },
    ...(cls.spellcasting
      ? [{ label: 'Spellcasting ability', value: cls.spellcasting.spellcasting_ability.name }]
      : []),
    ...(cls.subclasses && cls.subclasses.length > 0
      ? [{ label: 'Subclasses', value: joinNames(cls.subclasses) }]
      : []),
  ];
  const blocks: DetailBlock[] = [{ kind: 'stats', items: stats }];
  const skillChoices = (cls.proficiency_choices ?? []).map((choice) => choice.desc);
  if (skillChoices.length > 0) {
    blocks.push({ kind: 'text', title: 'Proficiency choices', paragraphs: skillChoices });
  }
  blocks.push({
    kind: 'text',
    title: 'Starting equipment',
    paragraphs: [...(equipment.length > 0 ? [equipment.join(', ')] : []), ...equipmentChoices],
  });
  blocks.push(...levelsTable(levels));
  return {
    index: cls.index,
    name: cls.name,
    tags: [],
    blocks,
  };
}
