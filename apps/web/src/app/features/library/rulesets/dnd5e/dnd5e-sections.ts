import { Dictionary } from '../../../../core/i18n/dictionary';
import { formatChallenge } from './mappers/monster.mapper';
import { ColumnDef, RulesetSection } from '../ruleset.model';

// Sections that SRD 2014 and SRD 2024 define the same way, written in the reader's language: each
// is made from the dictionary of the moment (see the rulesets' `sections`).

export function nameColumn(t: Dictionary): ColumnDef {
  return { key: 'name', label: t.library.columns.name, sortable: true };
}

export function plain(
  t: Dictionary,
  id: string,
  label: string,
  group: RulesetSection['group'],
  icon: RulesetSection['icon'],
  loadingMessage?: string,
): RulesetSection {
  return { id, label, group, icon, columns: [nameColumn(t)], filters: [], loadingMessage };
}

/** The spells of a ruleset, through GraphQL (the same query in SRD 2014 and 2024). */
export const SPELLS_QUERY = `{ spells(limit: 1000) { index name level school { name } classes { index name } ritual concentration components casting_time } }`;

const CHALLENGE_FRACTIONS: [number, string][] = [
  [0, '0'],
  [0.125, '1/8'],
  [0.25, '1/4'],
  [0.5, '1/2'],
];

/** The challenge ratings that SRD monsters have (2014 and 2024 alike; there is none at 18). */
const CHALLENGE_RATINGS = [
  ...CHALLENGE_FRACTIONS,
  ...[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 23, 24, 30].map(
    (value): [number, string] => [value, String(value)],
  ),
].map(([value, label]) => ({ value: String(value), label }));

/** The kinds of creatures the monster filter offers, as the API writes them. */
const MONSTER_TYPES = [
  'aberration',
  'beast',
  'celestial',
  'construct',
  'dragon',
  'elemental',
  'fey',
  'fiend',
  'giant',
  'humanoid',
  'monstrosity',
  'ooze',
  'plant',
  'swarm',
  'undead',
];

function yesNo(t: Dictionary) {
  return [
    { value: 'true', label: t.common.actions.yes },
    { value: 'false', label: t.common.actions.no },
  ];
}

/** Alignments and languages, one tab each (see `listPersonality`). */
export function personalitySection(t: Dictionary): RulesetSection {
  const texts = t.library;
  return {
    ...plain(
      t,
      'personality',
      texts.sections.personality,
      'players',
      'user',
      texts.sectionLoading.personality,
    ),
    tabs: {
      key: 'kind',
      label: texts.personalityTabs.label,
      options: [
        { value: 'Alignment', label: texts.personalityTabs.alignments },
        { value: 'Language', label: texts.personalityTabs.languages },
      ],
    },
  };
}

export function spellsSection(t: Dictionary): RulesetSection {
  const { columns, filters } = t.library;
  return {
    id: 'spells',
    label: t.library.sections.spells,
    group: 'players',
    icon: 'wand',
    // By level, then alphabetically within a level.
    defaultSort: 'level',
    loadingMessage: t.library.sectionLoading.spells,
    columns: [
      nameColumn(t),
      { key: 'level', label: columns.level, sortable: true },
      { key: 'school', label: columns.school, sortable: true },
      { key: 'classes', label: columns.classes, sortable: false },
      { key: 'castingTime', label: columns.castingTime, sortable: true },
      { key: 'components', label: columns.components, sortable: false },
      { key: 'ritual', label: columns.ritual, sortable: true },
      { key: 'concentration', label: columns.concentration, sortable: true },
    ],
    filters: [
      {
        key: 'level',
        label: filters.level,
        options: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map((level) => ({
          value: String(level),
          label: level === 0 ? filters.cantrip : filters.spellLevel(level),
        })),
      },
      // The schools, classes and casting times are the names the list has, in the language it
      // came in (and the casting times differ between SRD 2014 and 2024): no fixed list of words.
      { key: 'school', label: filters.school },
      { key: 'classes', label: filters.classes },
      { key: 'castingTime', label: filters.castingTime },
      {
        key: 'components',
        label: filters.components,
        options: [
          { value: 'V', label: filters.verbal },
          { value: 'S', label: filters.somatic },
          { value: 'M', label: filters.material },
        ],
      },
      { key: 'ritual', label: filters.ritual, options: yesNo(t) },
      { key: 'concentration', label: filters.concentration, options: yesNo(t) },
    ],
  };
}

export function monstersSection(t: Dictionary): RulesetSection {
  const { columns, filters, monsterTypes } = t.library;
  return {
    id: 'monsters',
    label: t.library.sections.monsters,
    group: 'master',
    icon: 'skull',
    loadingMessage: t.library.sectionLoading.monsters,
    columns: [
      nameColumn(t),
      { key: 'type', label: columns.type, sortable: true },
      { key: 'size', label: columns.size, sortable: true },
      {
        key: 'cr',
        label: columns.challenge,
        sortable: true,
        format: (value) => formatChallenge(Number(value)),
      },
    ],
    filters: [
      {
        key: 'type',
        label: filters.type,
        options: MONSTER_TYPES.map((type) => ({ value: type, label: monsterTypes[type] ?? type })),
      },
      { key: 'cr', label: filters.challenge, options: CHALLENGE_RATINGS },
    ],
  };
}
