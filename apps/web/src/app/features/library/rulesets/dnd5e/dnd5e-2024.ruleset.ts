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
  NAME_COLUMN,
  plain,
  SPELLS_QUERY,
  SPELLS_SECTION,
} from './dnd5e-sections';
import { ApiBackground, mapBackground } from './mappers/2024/background.mapper';
import { ApiFeat, mapFeat } from './mappers/2024/feat.mapper';
import { ApiMagicItem, mapMagicItem } from './mappers/2024/magic-item.mapper';
import { mapAlignment, mapLanguage } from './mappers/2024/personality.mapper';
import { ApiSpecies, ApiSpeciesTrait, mapSpecies } from './mappers/2024/species.mapper';
import { ApiWeaponMastery, mapWeaponMastery } from './mappers/2024/weapon-mastery.mapper';
import { ApiClass, ApiClassLevel, mapClass } from './mappers/class.mapper';
import { ApiRef } from './mappers/common';
import { ApiMonster, GqlMonster, mapMonster, mapMonsterSummary } from './mappers/monster.mapper';
import { ApiSpell, GqlSpell, mapSpell, mapSpellSummary } from './mappers/spell.mapper';

const VERSION = '2024';

// The 2024 GraphQL schema names its monsters `monsters2024`; `monsters` does not exist there.
const MONSTERS_QUERY = `{ monsters2024(limit: 1000) { index name type size challenge_rating } }`;

/** Sections that are a plain list of names on REST, read as is. */
const REST_LISTS = [
  'classes',
  'species',
  'backgrounds',
  'feats',
  'weapon-mastery-properties',
  'magic-items',
];

const SECTIONS: readonly RulesetSection[] = [
  plain('classes', 'Classes', 'players', 'sword'),
  plain('species', 'Espèces', 'players', 'users'),
  plain('backgrounds', 'Historiques', 'players', 'book'),
  plain('feats', 'Dons', 'players', 'star'),
  {
    ...plain('personality', 'Personnalité', 'players', 'user'),
    columns: [NAME_COLUMN, { key: 'kind', label: 'Type', sortable: true }],
  },
  SPELLS_SECTION,
  plain('weapon-mastery-properties', "Maîtrises d'armes", 'players', 'sword'),
  MONSTERS_SECTION,
  plain('magic-items', 'Objets magiques', 'master', 'crown'),
];

/** "DnD 5e — SRD 2024", read from dnd5eapi.co. It has no rules section. */
@Injectable({ providedIn: 'root' })
export class Dnd5e2024Ruleset implements Ruleset {
  readonly id = 'dnd5e-2024';
  readonly label = 'DnD 5e — SRD 2024';
  readonly sections = SECTIONS;
  readonly spellSectionId = 'spells';

  private readonly client = inject(Dnd5eClient);

  list(sectionId: string): Observable<EntrySummary[]> {
    if (sectionId === 'spells') {
      return this.client
        .graphql<{ spells: GqlSpell[] }>(VERSION, SPELLS_QUERY)
        .pipe(map((data) => data.spells.map(mapSpellSummary)));
    }
    if (sectionId === 'monsters') {
      return this.client
        .graphql<{ monsters2024: GqlMonster[] }>(VERSION, MONSTERS_QUERY)
        .pipe(map((data) => data.monsters2024.map(mapMonsterSummary)));
    }
    if (sectionId === 'personality') {
      return listPersonality(this.client, VERSION);
    }
    if (REST_LISTS.includes(sectionId)) {
      return this.refs(sectionId).pipe(
        map((refs) => refs.map((ref) => ({ index: ref.index, name: ref.name, fields: {} }))),
      );
    }
    return of([]);
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
      case 'species':
        return this.client
          .rest<ApiSpecies>(VERSION, `species/${index}`)
          .pipe(
            switchMap((species) =>
              fetchTraits<ApiSpeciesTrait>(this.client, VERSION, species.traits).pipe(
                map((traits) => mapSpecies(species, traits)),
              ),
            ),
          );
      case 'backgrounds':
        return this.client
          .rest<ApiBackground>(VERSION, `backgrounds/${index}`)
          .pipe(map(mapBackground));
      case 'feats':
        return this.client.rest<ApiFeat>(VERSION, `feats/${index}`).pipe(map(mapFeat));
      case 'weapon-mastery-properties':
        return this.client
          .rest<ApiWeaponMastery>(VERSION, `weapon-mastery-properties/${index}`)
          .pipe(map(mapWeaponMastery));
      case 'magic-items':
        return this.client
          .rest<ApiMagicItem>(VERSION, `magic-items/${index}`)
          .pipe(map(mapMagicItem));
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
