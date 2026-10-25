import { DetailBlock, EntryDetail, TableCell } from '../../ruleset.model';
import { ApiRef, EntryTexts, joinNames, paragraphs, slug, SOURCE_TEXTS } from './common';

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

/** One feature of a class or of one of its subclasses, from GraphQL. */
export interface GqlFeature {
  index: string;
  name: string;
  /** A number in SRD 2014, an object in SRD 2024. */
  level: number | { level: number };
  /** SRD 2014: a list of paragraphs. */
  desc?: string[];
  /** SRD 2024: one string. */
  description?: string;
  subclass: { index: string } | null;
}

export interface GqlSubclass {
  index: string;
  name: string;
  /** SRD 2014. */
  desc?: string[];
  /** SRD 2014: what the class calls its subclasses ("Arcane Tradition"). */
  subclass_flavor?: string;
  /** SRD 2024. */
  description?: string;
  /** SRD 2024: a one-line pitch ("Create Explosive Elemental Effects"). */
  summary?: string;
}

/** The features and subclasses of a class, read in one GraphQL request. */
export interface GqlClassFeatures {
  features: GqlFeature[];
  class: { subclasses: GqlSubclass[] } | null;
}

/** The query answering `GqlClassFeatures` per SRD version; `$class` is the class index. */
export const CLASS_FEATURES_QUERIES = {
  '2014': `query ($class: String!) { features(class: [$class], limit: 200) { index name level desc subclass { index } } class(index: $class) { subclasses { index name desc subclass_flavor } } }`,
  '2024': `query ($class: String!) { features(class: [$class], limit: 200) { index name level { level } description subclass { index } } class(index: $class) { subclasses { index name description summary } } }`,
} as const;

const SLOT_LEVELS = [1, 2, 3, 4, 5, 6, 7, 8, 9];

function featureLevel(feature: GqlFeature): number {
  return typeof feature.level === 'number' ? feature.level : feature.level.level;
}

/** The id in-page links use for the subclasses heading. */
export const SUBCLASSES_ANCHOR = 'subclasses';

/** The id of a feature's entry, built from its index (the levels table links to it). */
export function featureAnchor(index: string): string {
  return `feature-${slug(index)}`;
}

interface FeatureEntries {
  items: { name: string; text: string; anchor: string }[];
  /** Index of every feature → the id of the entry it ended up in. */
  anchors: Map<string, string>;
}

/**
 * The features as entries, by level. SRD 2014 repeats a feature at each level it is gained again
 * ("Extra Attack (2)", "Indomitable (2 uses)"), with the same text: such repeats become one entry
 * naming all of its levels, whose anchor every one of them leads to.
 */
function featureEntries(features: readonly GqlFeature[], t: EntryTexts): FeatureEntries {
  const merged = new Map<
    string,
    { name: string; levels: number[]; text: string; anchor: string }
  >();
  const anchors = new Map<string, string>();
  const byLevel = [...features].sort(
    (a, b) => featureLevel(a) - featureLevel(b) || a.name.localeCompare(b.name),
  );
  for (const feature of byLevel) {
    const text = paragraphs(feature.desc ?? feature.description).join('\n\n');
    // A feature without text is keyed by its index: it is never taken for a repeat.
    const key = text || feature.index;
    const known = merged.get(key);
    if (known !== undefined) {
      known.levels.push(featureLevel(feature));
      // "Extra Attack (2)" and "Extra Attack (3)" together are just "Extra Attack".
      known.name = known.name.replace(/\s*\([^)]*\)$/, '');
      anchors.set(feature.index, known.anchor);
    } else {
      const anchor = featureAnchor(feature.index);
      merged.set(key, { name: feature.name, levels: [featureLevel(feature)], text, anchor });
      anchors.set(feature.index, anchor);
    }
  }
  const items = [...merged.values()].map(({ name, levels, text, anchor }) => ({
    name: `${name} (${t.atLevels(levels)})`,
    text,
    anchor,
  }));
  return { items, anchors };
}

interface FeatureBlocks {
  blocks: DetailBlock[];
  /** Where a feature of the levels table leads, if anywhere. */
  anchorOf: (feature: ApiRef) => string | undefined;
}

/**
 * The class features, then, under a « Subclasses » heading, each subclass: its description and
 * its own features. In the levels table, a feature that only says « gain a subclass feature » (SRD
 * 2014 names it after the kind of subclass, "Arcane Tradition feature"; SRD 2024 indexes it
 * `<class>-subclass`) leads to that heading; any other to its entry.
 */
function featureBlocks(extra: GqlClassFeatures | undefined, t: EntryTexts): FeatureBlocks {
  if (extra === undefined) {
    return { blocks: [], anchorOf: () => undefined };
  }
  const blocks: DetailBlock[] = [];
  const own = featureEntries(
    extra.features.filter((feature) => feature.subclass === null),
    t,
  );
  if (own.items.length > 0) {
    blocks.push({ kind: 'entries', title: t.classFeatures, items: own.items });
  }
  const subclasses = extra.class?.subclasses ?? [];
  if (subclasses.length > 0) {
    blocks.push({ kind: 'text', title: t.subclasses, anchor: SUBCLASSES_ANCHOR, paragraphs: [] });
  }
  for (const subclass of subclasses) {
    const kind = subclass.subclass_flavor ?? t.subclass;
    blocks.push({
      kind: 'text',
      title: `${kind}: ${subclass.name}`,
      nested: true,
      paragraphs: [
        ...(subclass.summary ? [`*${subclass.summary}*`] : []),
        ...paragraphs(subclass.desc ?? subclass.description),
      ],
    });
    const features = extra.features.filter((feature) => feature.subclass?.index === subclass.index);
    if (features.length > 0) {
      blocks.push({
        kind: 'entries',
        title: t.subclassFeatures(subclass.name),
        nested: true,
        items: featureEntries(features, t).items,
      });
    }
  }
  const flavors = subclasses.flatMap((subclass) =>
    subclass.subclass_flavor ? [subclass.subclass_flavor] : [],
  );
  const leadsToSubclasses = (feature: ApiRef) =>
    subclasses.length > 0 &&
    (/(^|-)subclass$/.test(feature.index) ||
      flavors.some((flavor) => feature.name.startsWith(flavor)));
  return {
    blocks,
    anchorOf: (feature) =>
      leadsToSubclasses(feature) ? SUBCLASSES_ANCHOR : own.anchors.get(feature.index),
  };
}

/**
 * The levels table: level, proficiency bonus, features (each a link when `anchorOf` knows where it
 * is described), and the spellcasting columns in use.
 */
function levelsTable(
  levels: ApiClassLevel[],
  anchorOf: FeatureBlocks['anchorOf'],
  t: EntryTexts,
): DetailBlock[] {
  if (levels.length === 0) {
    return [];
  }
  const cantrips = levels.some((level) => level.spellcasting?.['cantrips_known'] !== undefined);
  const prepared = levels.some((level) => level.spellcasting?.['prepared_spells'] !== undefined);
  const slots = SLOT_LEVELS.filter((slot) =>
    levels.some((level) => (level.spellcasting?.[`spell_slots_level_${slot}`] ?? 0) > 0),
  );
  const columns = [
    t.level,
    t.proficiencyBonus,
    t.features,
    ...(cantrips ? [t.cantrips] : []),
    ...(prepared ? [t.preparedSpells] : []),
    ...slots.map((slot) => t.slot(slot)),
  ];
  const rows = levels.map((level): TableCell[] => [
    String(level.level),
    `+${level.prof_bonus}`,
    level.features.length > 0
      ? level.features.map((feature) => ({ text: feature.name, anchor: anchorOf(feature) }))
      : '—',
    ...(cantrips ? [String(level.spellcasting?.['cantrips_known'] ?? 0)] : []),
    ...(prepared ? [String(level.spellcasting?.['prepared_spells'] ?? 0)] : []),
    ...slots.map((slot) => String(level.spellcasting?.[`spell_slots_level_${slot}`] ?? 0)),
  ]);
  return [{ kind: 'table', title: t.classLevels, columns, rows }];
}

/**
 * The API lists the saving throws among the proficiencies too. They are told apart by their
 * identifier (`saving-throw-int`), never by their name (« Saving Throw: INT »), which depends on
 * the language of the data.
 */
const SAVING_THROW_PREFIX = 'saving-throw-';

/**
 * The saving throws on a line of their own and the other proficiencies apart: the saving throws
 * are not repeated among the proficiencies. Without the class's own list, they are read from the
 * identifiers of the proficiencies (`saving-throw-int` gives INT).
 */
function splitProficiencies(cls: ApiClass, t: EntryTexts): { saves: string; others: string } {
  const all = cls.proficiencies ?? [];
  const isSave = (ref: ApiRef) => ref.index.startsWith(SAVING_THROW_PREFIX);
  const others = all.filter((ref) => !isSave(ref));
  const fromProficiencies = all.filter(isSave).map((ref) => {
    const ability = ref.index.slice(SAVING_THROW_PREFIX.length);
    return { index: ability, name: ability.toUpperCase() };
  });
  const saves = cls.saving_throws?.length > 0 ? cls.saving_throws : fromProficiencies;
  return { saves: joinNames(saves, t.none), others: joinNames(others, t.none) };
}

export function mapClass(
  cls: ApiClass,
  levels: ApiClassLevel[],
  extra?: GqlClassFeatures,
  t: EntryTexts = SOURCE_TEXTS,
): EntryDetail {
  const equipment = (cls.starting_equipment ?? []).map(
    (item) => `${item.equipment.name}${item.quantity > 1 ? ` x${item.quantity}` : ''}`,
  );
  const equipmentChoices = (cls.starting_equipment_options ?? []).map((option) => option.desc);
  const proficiencies = splitProficiencies(cls, t);
  const stats = [
    { label: t.hitDie, value: `d${cls.hit_die}` },
    ...(cls.primary_ability ? [{ label: t.primaryAbility, value: cls.primary_ability.desc }] : []),
    { label: t.savingThrows, value: proficiencies.saves },
    { label: t.proficiencies, value: proficiencies.others },
    ...(cls.spellcasting
      ? [{ label: t.spellcastingAbility, value: cls.spellcasting.spellcasting_ability.name }]
      : []),
    ...(cls.subclasses && cls.subclasses.length > 0
      ? [{ label: t.subclasses, value: joinNames(cls.subclasses, t.none) }]
      : []),
  ];
  // One line per stat: the proficiencies alone can fill a whole line.
  const blocks: DetailBlock[] = [{ kind: 'stats', layout: 'rows', items: stats }];
  const skillChoices = (cls.proficiency_choices ?? []).map((choice) => choice.desc);
  if (skillChoices.length > 0) {
    blocks.push({ kind: 'text', title: t.proficiencyChoices, paragraphs: skillChoices });
  }
  blocks.push({
    kind: 'text',
    title: t.startingEquipment,
    paragraphs: [...(equipment.length > 0 ? [equipment.join(', ')] : []), ...equipmentChoices],
  });
  const features = featureBlocks(extra, t);
  blocks.push(...levelsTable(levels, features.anchorOf, t));
  blocks.push(...features.blocks);
  return {
    index: cls.index,
    name: cls.name,
    tags: [],
    blocks,
  };
}
