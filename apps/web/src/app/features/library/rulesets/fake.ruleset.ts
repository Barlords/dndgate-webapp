import { Observable, of, throwError } from 'rxjs';
import { EntryDetail, EntrySummary, Ruleset, RulesetSection } from './ruleset.model';

/** For tests only: an in-memory ruleset with spells, monsters and rules. */

const SPELLS: RulesetSection = {
  id: 'spells',
  label: 'Sorts',
  group: 'players',
  icon: 'wand',
  columns: [
    { key: 'name', label: 'Nom', sortable: true },
    { key: 'level', label: 'Niveau', sortable: true },
    { key: 'school', label: 'École', sortable: false },
  ],
  filters: [
    {
      key: 'level',
      label: 'Niveau',
      options: [
        { value: '0', label: 'Tour de magie' },
        { value: '1', label: 'Niveau 1' },
        { value: '3', label: 'Niveau 3' },
      ],
    },
    {
      key: 'school',
      label: 'École',
      options: [
        { value: 'Evocation', label: 'Évocation' },
        { value: 'Abjuration', label: 'Abjuration' },
      ],
    },
  ],
};

const MONSTERS: RulesetSection = {
  id: 'monsters',
  label: 'Monstres',
  group: 'master',
  icon: 'skull',
  columns: [
    { key: 'name', label: 'Nom', sortable: true },
    { key: 'tags', label: 'Mots-clés', sortable: false },
    { key: 'legendary', label: 'Légendaire', sortable: false },
  ],
  filters: [],
};

const RULES: RulesetSection = {
  id: 'rules',
  label: 'Règles',
  group: 'rules',
  icon: 'scroll',
  columns: [{ key: 'name', label: 'Nom', sortable: true }],
  filters: [],
};

export const FAKE_SPELLS: EntrySummary[] = [
  { index: 'fireball', name: 'Fireball', fields: { level: 3, school: 'Evocation' } },
  { index: 'eclair', name: 'Éclair', fields: { level: 3, school: 'Evocation' } },
  { index: 'shield', name: 'Shield', fields: { level: 1, school: 'Abjuration' } },
  { index: 'light', name: 'Light', fields: { level: 0, school: 'Evocation' } },
];

export const FAKE_MONSTERS: EntrySummary[] = [
  { index: 'dragon', name: 'Dragon', fields: { tags: ['big', 'old'], legendary: true } },
  { index: 'goblin', name: 'Goblin', fields: { tags: [], legendary: false } },
];

export const FAKE_FIREBALL: EntryDetail = {
  index: 'fireball',
  name: 'Fireball',
  subtitle: '3rd-level evocation',
  tags: ['Wizard', 'Sorcerer'],
  blocks: [
    { kind: 'stats', items: [{ label: 'Range', value: '150 feet' }] },
    { kind: 'text', title: 'Description', paragraphs: ['A bright streak **explodes**.'] },
  ],
};

export interface FakeRulesetOptions {
  id?: string;
  label?: string;
  /** Without spells: no spell section and `spellSectionId` null. */
  spells?: boolean;
}

export class FakeRuleset implements Ruleset {
  readonly id: string;
  readonly label: string;
  readonly sections: RulesetSection[];
  readonly spellSectionId: string | null;

  constructor({
    id = 'dnd5e-2014',
    label = 'Jeu factice',
    spells = true,
  }: FakeRulesetOptions = {}) {
    this.id = id;
    this.label = label;
    // A copy per instance: a test may change its sections.
    this.sections = structuredClone(spells ? [SPELLS, MONSTERS, RULES] : [MONSTERS]);
    this.spellSectionId = spells ? 'spells' : null;
  }

  list(sectionId: string): Observable<EntrySummary[]> {
    switch (sectionId) {
      case 'spells':
        return of(FAKE_SPELLS);
      case 'monsters':
        return of(FAKE_MONSTERS);
      default:
        return of([]);
    }
  }

  detail(sectionId: string, index: string): Observable<EntryDetail> {
    return sectionId === 'spells' && index === 'fireball'
      ? of(FAKE_FIREBALL)
      : throwError(() => new Error(`No entry ${sectionId}/${index}`));
  }
}
