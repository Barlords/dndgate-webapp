import { formatChallenge } from './mappers/monster.mapper';
import { RulesetSection } from '../ruleset.model';

// Sections that SRD 2014 and SRD 2024 define the same way.

export const NAME_COLUMN = { key: 'name', label: 'Nom', sortable: true };

export function plain(
  id: string,
  label: string,
  group: RulesetSection['group'],
  icon: RulesetSection['icon'],
  loadingMessage?: string,
): RulesetSection {
  return { id, label, group, icon, columns: [NAME_COLUMN], filters: [], loadingMessage };
}

export function options(values: string[]): { value: string; label: string }[] {
  return values.map((value) => ({ value, label: value }));
}

/** The spells of a ruleset, through GraphQL (the same query in SRD 2014 and 2024). */
export const SPELLS_QUERY = `{ spells(limit: 1000) { index name level school { name } classes { index name } ritual concentration components casting_time } }`;

const YES_NO = [
  { value: 'true', label: 'Oui' },
  { value: 'false', label: 'Non' },
];

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

/** Alignments and languages, one tab each (see `listPersonality`). */
export const PERSONALITY_SECTION: RulesetSection = {
  ...plain(
    'personality',
    'Personnalité',
    'players',
    'user',
    'Le sage déchiffre les langues anciennes…',
  ),
  tabs: {
    key: 'kind',
    label: 'Type',
    options: [
      { value: 'Alignment', label: 'Alignements' },
      { value: 'Language', label: 'Langues' },
    ],
  },
};

export const SPELLS_SECTION: RulesetSection = {
  id: 'spells',
  label: 'Sorts',
  group: 'players',
  icon: 'wand',
  // By level, then alphabetically within a level.
  defaultSort: 'level',
  loadingMessage: 'Demande en cours à Mystra…',
  columns: [
    NAME_COLUMN,
    { key: 'level', label: 'Niveau', sortable: true },
    { key: 'school', label: 'École', sortable: true },
    { key: 'classes', label: 'Classes', sortable: false },
    { key: 'castingTime', label: 'Incantation', sortable: true },
    { key: 'components', label: 'Composantes', sortable: false },
    { key: 'ritual', label: 'Rituel', sortable: true },
    { key: 'concentration', label: 'Concentration', sortable: true },
  ],
  filters: [
    { key: 'level', label: 'Niveau', options: SPELL_LEVELS },
    // The schools, classes and casting times are the names the list has, in the language it came
    // in (and the casting times differ between SRD 2014 and 2024): no fixed list of English words.
    { key: 'school', label: 'École' },
    { key: 'classes', label: 'Classe' },
    { key: 'castingTime', label: "Temps d'incantation" },
    {
      key: 'components',
      label: 'Composantes',
      options: [
        { value: 'V', label: 'Verbale (V)' },
        { value: 'S', label: 'Somatique (S)' },
        { value: 'M', label: 'Matérielle (M)' },
      ],
    },
    { key: 'ritual', label: 'Rituel', options: YES_NO },
    { key: 'concentration', label: 'Concentration', options: YES_NO },
  ],
};

export const MONSTERS_SECTION: RulesetSection = {
  id: 'monsters',
  label: 'Monstres',
  group: 'master',
  icon: 'skull',
  loadingMessage: 'En attente du retour des explorateurs…',
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
