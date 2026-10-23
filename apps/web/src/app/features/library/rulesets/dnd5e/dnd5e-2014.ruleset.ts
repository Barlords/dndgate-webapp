import { inject, Injectable } from '@angular/core';
import { catchError, forkJoin, map, Observable, of, switchMap, throwError } from 'rxjs';
import {
  EntryDetail,
  EntrySummary,
  Ruleset,
  RulesetEntryNotFoundError,
  RulesetSection,
} from '../ruleset.model';
import { Dnd5eClient } from './dnd5e-client';
import { mapBackground, ApiBackground } from './mappers/background.mapper';
import { ApiClass, ApiClassLevel, mapClass } from './mappers/class.mapper';
import { ApiList, ApiRef } from './mappers/common';
import {
  ApiMonster,
  formatChallenge,
  GqlMonster,
  mapMonster,
  mapMonsterSummary,
} from './mappers/monster.mapper';
import {
  ALIGNMENT_PREFIX,
  ApiAlignment,
  ApiLanguage,
  LANGUAGE_PREFIX,
  mapAlignment,
  mapAlignmentSummary,
  mapLanguage,
  mapLanguageSummary,
} from './mappers/personality.mapper';
import { ApiRace, ApiTrait, mapRace } from './mappers/race.mapper';
import { ApiRule, mapRule } from './mappers/rule.mapper';
import { ApiSpell, GqlSpell, mapSpell, mapSpellSummary } from './mappers/spell.mapper';

const VERSION = '2014';

const SPELLS_QUERY = `{ spells(limit: 1000) { index name level school { name } classes { index name } ritual concentration } }`;
const MONSTERS_QUERY = `{ monsters(limit: 1000) { index name type size challenge_rating } }`;

const NAME_COLUMN = { key: 'name', label: 'Nom', sortable: true };

function plain(
  id: string,
  label: string,
  group: RulesetSection['group'],
  icon: RulesetSection['icon'],
): RulesetSection {
  return { id, label, group, icon, columns: [NAME_COLUMN], filters: [] };
}

function options(values: string[]): { value: string; label: string }[] {
  return values.map((value) => ({ value, label: value }));
}

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

/** The challenge ratings that SRD 2014 monsters have (there is none at 18). */
const CHALLENGE_RATINGS = [
  ...CHALLENGE_FRACTIONS,
  ...[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 23, 24, 30].map(
    (value): [number, string] => [value, String(value)],
  ),
].map(([value, label]) => ({ value: String(value), label }));

const SECTIONS: readonly RulesetSection[] = [
  plain('classes', 'Classes', 'players', 'sword'),
  plain('races', 'Races', 'players', 'users'),
  plain('backgrounds', 'Historiques', 'players', 'book'),
  {
    ...plain('personality', 'Personnalité', 'players', 'user'),
    columns: [NAME_COLUMN, { key: 'kind', label: 'Type', sortable: true }],
  },
  {
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
  },
  {
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
  },
  plain('rules', 'Règles', 'rules', 'scroll'),
];

/** "DnD 5e — SRD 2014", read from dnd5eapi.co. */
@Injectable({ providedIn: 'root' })
export class Dnd5e2014Ruleset implements Ruleset {
  readonly id = 'dnd5e-2014';
  readonly label = 'DnD 5e — SRD 2014';
  readonly sections = SECTIONS;
  readonly spellSectionId = 'spells';

  private readonly client = inject(Dnd5eClient);

  list(sectionId: string): Observable<EntrySummary[]> {
    switch (sectionId) {
      case 'spells':
        return this.client
          .graphql<{ spells: GqlSpell[] }>(VERSION, SPELLS_QUERY)
          .pipe(map((data) => data.spells.map(mapSpellSummary)));
      case 'monsters':
        return this.client
          .graphql<{ monsters: GqlMonster[] }>(VERSION, MONSTERS_QUERY)
          .pipe(map((data) => data.monsters.map(mapMonsterSummary)));
      case 'personality':
        return forkJoin([this.refs('alignments'), this.refs('languages')]).pipe(
          map(([alignments, languages]) => [
            ...alignments.map(mapAlignmentSummary),
            ...languages.map(mapLanguageSummary),
          ]),
        );
      case 'classes':
      case 'races':
      case 'backgrounds':
      case 'rules':
        return this.refs(sectionId).pipe(
          map((refs) => refs.map((ref) => ({ index: ref.index, name: ref.name, fields: {} }))),
        );
      default:
        return of([]);
    }
  }

  detail(sectionId: string, index: string): Observable<EntryDetail> {
    switch (sectionId) {
      case 'spells':
        return this.client.rest<ApiSpell>(VERSION, `spells/${index}`).pipe(map(mapSpell));
      case 'monsters':
        return this.client.rest<ApiMonster>(VERSION, `monsters/${index}`).pipe(map(mapMonster));
      case 'classes':
        return forkJoin([
          this.client.rest<ApiClass>(VERSION, `classes/${index}`),
          this.client.rest<ApiClassLevel[]>(VERSION, `classes/${index}/levels`),
        ]).pipe(map(([cls, levels]) => mapClass(cls, levels)));
      case 'races':
        return this.client
          .rest<ApiRace>(VERSION, `races/${index}`)
          .pipe(
            switchMap((race) => this.traits(race).pipe(map((traits) => mapRace(race, traits)))),
          );
      case 'backgrounds':
        return this.client
          .rest<ApiBackground>(VERSION, `backgrounds/${index}`)
          .pipe(map(mapBackground));
      case 'rules':
        return this.client.rest<ApiRule>(VERSION, `rules/${index}`).pipe(map(mapRule));
      case 'personality':
        return this.personality(index);
      default:
        return throwError(() => new RulesetEntryNotFoundError(`No section ${sectionId}`));
    }
  }

  private refs(resource: string): Observable<ApiRef[]> {
    return this.client.rest<ApiList>(VERSION, resource).pipe(map((list) => list.results));
  }

  private personality(index: string): Observable<EntryDetail> {
    if (index.startsWith(ALIGNMENT_PREFIX)) {
      return this.client
        .rest<ApiAlignment>(VERSION, `alignments/${index.slice(ALIGNMENT_PREFIX.length)}`)
        .pipe(map(mapAlignment));
    }
    if (index.startsWith(LANGUAGE_PREFIX)) {
      return this.client
        .rest<ApiLanguage>(VERSION, `languages/${index.slice(LANGUAGE_PREFIX.length)}`)
        .pipe(map(mapLanguage));
    }
    return throwError(() => new RulesetEntryNotFoundError(`No personality entry ${index}`));
  }

  /** The traits of a race; one that is gone (404) is left out. */
  private traits(race: ApiRace): Observable<ApiTrait[]> {
    if (race.traits.length === 0) {
      return of([]);
    }
    return forkJoin(
      race.traits.map((trait) =>
        this.client.rest<ApiTrait>(VERSION, `traits/${trait.index}`).pipe(
          // A trait that no longer exists is skipped; any other failure fails the race.
          catchError((error: unknown) =>
            error instanceof RulesetEntryNotFoundError ? of(null) : throwError(() => error),
          ),
        ),
      ),
    ).pipe(map((traits) => traits.filter((trait): trait is ApiTrait => trait !== null)));
  }
}
