import { inject, Injectable } from '@angular/core';
import { forkJoin, map, Observable, of, switchMap, throwError } from 'rxjs';
import {
  EntryDetail,
  EntrySummary,
  Ruleset,
  RulesetEntryNotFoundError,
  RulesetSection,
} from '../ruleset.model';
import { Dnd5eClient } from './dnd5e-client';
import { fetchTraits, listPersonality, listRefs, personalityDetail } from './dnd5e-resources';
import {
  MONSTERS_SECTION,
  PERSONALITY_SECTION,
  plain,
  SPELLS_QUERY,
  SPELLS_SECTION,
} from './dnd5e-sections';
import { mapBackground, ApiBackground } from './mappers/background.mapper';
import {
  ApiClass,
  ApiClassLevel,
  CLASS_FEATURES_QUERIES,
  GqlClassFeatures,
  mapClass,
} from './mappers/class.mapper';
import { ApiRef } from './mappers/common';
import { ApiMonster, GqlMonster, mapMonster, mapMonsterSummary } from './mappers/monster.mapper';
import { mapAlignment, mapLanguage } from './mappers/personality.mapper';
import { ApiRace, ApiTrait, mapRace } from './mappers/race.mapper';
import { ApiRule, mapRule } from './mappers/rule.mapper';
import { ApiSpell, GqlSpell, mapSpell, mapSpellSummary } from './mappers/spell.mapper';

const VERSION = '2014';

const MONSTERS_QUERY = `{ monsters(limit: 1000) { index name type size challenge_rating } }`;

const SECTIONS: readonly RulesetSection[] = [
  plain('classes', 'Classes', 'players', 'sword', 'Les maîtres de guilde ouvrent leurs registres…'),
  plain('races', 'Races', 'players', 'users', 'Recensement des peuples du royaume…'),
  plain('backgrounds', 'Historiques', 'players', 'book', 'Le barde rassemble ses souvenirs…'),
  PERSONALITY_SECTION,
  SPELLS_SECTION,
  MONSTERS_SECTION,
  plain('rules', 'Règles', 'rules', 'scroll', 'Le MJ relit ses règles…'),
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
        return listPersonality(this.client, VERSION);
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
          this.client.graphql<GqlClassFeatures>(VERSION, CLASS_FEATURES_QUERIES[VERSION], {
            class: index,
          }),
        ]).pipe(map(([cls, levels, features]) => mapClass(cls, levels, features)));
      case 'races':
        return this.client
          .rest<ApiRace>(VERSION, `races/${index}`)
          .pipe(
            switchMap((race) =>
              fetchTraits<ApiTrait>(this.client, VERSION, race.traits).pipe(
                map((traits) => mapRace(race, traits)),
              ),
            ),
          );
      case 'backgrounds':
        return this.client
          .rest<ApiBackground>(VERSION, `backgrounds/${index}`)
          .pipe(map(mapBackground));
      case 'rules':
        return this.client.rest<ApiRule>(VERSION, `rules/${index}`).pipe(map(mapRule));
      case 'personality':
        return personalityDetail(this.client, VERSION, index, { mapAlignment, mapLanguage });
      default:
        return throwError(() => new RulesetEntryNotFoundError(`No section ${sectionId}`));
    }
  }

  private refs(resource: string): Observable<ApiRef[]> {
    return listRefs(this.client, VERSION, resource);
  }
}
