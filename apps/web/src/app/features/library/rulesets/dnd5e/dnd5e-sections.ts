import { formatChallenge } from './mappers/monster.mapper';
import { RulesetSection } from '../ruleset.model';

// Sections that SRD 2014 and SRD 2024 define the same way.

export const NAME_COLUMN = { key: 'name', label: 'Nom', sortable: true };

export function plain(
  id: string,
  label: string,
  group: RulesetSection['group'],
  icon: RulesetSection['icon'],
): RulesetSection {
  return { id, label, group, icon, columns: [NAME_COLUMN], filters: [] };
}

export function options(values: string[]): { value: string; label: string }[] {
  return values.map((value) => ({ value, label: value }));
}

/** The spells of a ruleset, through GraphQL (the same query in SRD 2014 and 2024). */
export const SPELLS_QUERY = `{ spells(limit: 1000) { index name level school { name } classes { index name } ritual concentration } }`;

const SPELL_LEVELS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map((level) => ({
  value: String(level),
  label: level === 0 ? 'Tour de magie' : `Niveau ${level}`,
}));

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

export const SPELLS_SECTION: RulesetSection = {
  id: 'spells',
  label: 'Sorts',
  group: 'players',
  icon: 'wand',
  columns: [
    NAME_COLUMN,
    { key: 'level', label: 'Niveau', sortable: true },
    { key: 'school', label: 'École', sortable: true },
    { key: 'classes', label: 'Classes', sortable: false },
  ],
  filters: [
    { key: 'level', label: 'Niveau', options: SPELL_LEVELS },
    {
      key: 'school',
      label: 'École',
      options: options([
        'Abjuration',
        'Conjuration',
        'Divination',
        'Enchantment',
        'Evocation',
        'Illusion',
        'Necromancy',
        'Transmutation',
      ]),
    },
    {
      key: 'classes',
      label: 'Classe',
      options: options([
        'Bard',
        'Cleric',
        'Druid',
        'Paladin',
        'Ranger',
        'Sorcerer',
        'Warlock',
        'Wizard',
      ]),
    },
  ],
};

export const MONSTERS_SECTION: RulesetSection = {
  id: 'monsters',
  label: 'Monstres',
  group: 'master',
  icon: 'skull',
  columns: [
    NAME_COLUMN,
    { key: 'type', label: 'Type', sortable: true },
    { key: 'size', label: 'Taille', sortable: true },
    { key: 'cr', label: 'FP', sortable: true, format: (value) => formatChallenge(Number(value)) },
  ],
  filters: [
    {
      key: 'type',
      label: 'Type',
      options: options([
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
      ]),
    },
    { key: 'cr', label: 'FP', options: CHALLENGE_RATINGS },
  ],
};
