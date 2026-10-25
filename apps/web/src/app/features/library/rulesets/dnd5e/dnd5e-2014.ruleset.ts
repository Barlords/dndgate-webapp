import { computed, inject, Injectable } from '@angular/core';
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
  monstersSection,
  personalitySection,
  plain,
  SPELLS_QUERY,
  spellsSection,
} from './dnd5e-sections';
import { mapBackground, ApiBackground } from './mappers/background.mapper';
import {
  ApiClass,
  ApiClassLevel,
  CLASS_FEATURES_QUERIES,
  GqlClassFeatures,
  mapClass,
} from './mappers/class.mapper';
import { ApiRef, EntryTexts } from './mappers/common';
import { ApiMonster, GqlMonster, mapMonster, mapMonsterSummary } from './mappers/monster.mapper';
import { ApiAlignment, ApiLanguage, mapAlignment, mapLanguage } from './mappers/personality.mapper';
import { ApiRace, ApiTrait, mapRace } from './mappers/race.mapper';
import { ApiRule, mapRule } from './mappers/rule.mapper';
import { ApiSpell, GqlSpell, mapSpell, mapSpellSummary } from './mappers/spell.mapper';
import { I18n } from '../../../../core/i18n/i18n.service';
import { Dictionary } from '../../../../core/i18n/dictionary';

const VERSION = '2014';

const MONSTERS_QUERY = `{ monsters(limit: 1000) { index name type size challenge_rating } }`;

function sections(t: Dictionary): readonly RulesetSection[] {
  const { sections: label, sectionLoading: loading } = t.library;
  return [
    plain(t, 'classes', label.classes, 'players', 'sword', loading.classes),
    plain(t, 'races', label.races, 'players', 'users', loading.peoples),
    plain(t, 'backgrounds', label.backgrounds, 'players', 'book', loading.backgrounds),
    personalitySection(t),
    spellsSection(t),
    monstersSection(t),
    plain(t, 'rules', label.rules, 'rules', 'scroll', loading.rules),
  ];
}

/** "DnD 5e — SRD 2014", read from dnd5eapi.co. */
@Injectable({ providedIn: 'root' })
export class Dnd5e2014Ruleset implements Ruleset {
  readonly id = 'dnd5e-2014';
  readonly label = 'DnD 5e — SRD 2014';
  private readonly i18n = inject(I18n);
  private readonly sectionList = computed(() => sections(this.i18n.t()));
  /** In the reader's language, made again when it changes. */
  get sections(): readonly RulesetSection[] {
    return this.sectionList();
  }
  readonly spellSectionId = 'spells';

  private readonly client = inject(Dnd5eClient);

  /** The personality section reads the alignments and the languages, translated alike. */
  untranslated(sectionId: string): boolean {
    return this.client.untranslated(
      VERSION,
      sectionId === 'personality' ? 'alignments' : sectionId,
    );
  }

  list(sectionId: string): Observable<EntrySummary[]> {
    switch (sectionId) {
      case 'spells':
        return this.client
          .graphql<{ spells: GqlSpell[] }>(VERSION, 'spells', SPELLS_QUERY)
          .pipe(map((data) => data.spells.map(mapSpellSummary)));
      case 'monsters':
        return this.client
          .graphql<{ monsters: GqlMonster[] }>(VERSION, 'monsters', MONSTERS_QUERY)
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
        return this.client
          .rest<ApiSpell>(VERSION, `spells/${index}`)
          .pipe(map((spell) => mapSpell(spell, this.texts())));
      case 'monsters':
        return this.client
          .rest<ApiMonster>(VERSION, `monsters/${index}`)
          .pipe(map((monster) => mapMonster(monster, this.texts())));
      case 'classes':
        return forkJoin([
          this.client.rest<ApiClass>(VERSION, `classes/${index}`),
          this.client.rest<ApiClassLevel[]>(VERSION, `classes/${index}/levels`),
          this.client.graphql<GqlClassFeatures>(
            VERSION,
            'features',
            CLASS_FEATURES_QUERIES[VERSION],
            {
              class: index,
            },
          ),
        ]).pipe(map(([cls, levels, features]) => mapClass(cls, levels, features, this.texts())));
      case 'races':
        return this.client
          .rest<ApiRace>(VERSION, `races/${index}`)
          .pipe(
            switchMap((race) =>
              fetchTraits<ApiTrait>(this.client, VERSION, race.traits).pipe(
                map((traits) => mapRace(race, traits, this.texts())),
              ),
            ),
          );
      case 'backgrounds':
        return this.client
          .rest<ApiBackground>(VERSION, `backgrounds/${index}`)
          .pipe(map((background) => mapBackground(background, this.texts())));
      case 'rules':
        return this.client
          .rest<ApiRule>(VERSION, `rules/${index}`)
          .pipe(map((rule) => mapRule(rule, this.texts())));
      case 'personality':
        return personalityDetail<ApiAlignment, ApiLanguage>(this.client, VERSION, index, {
          mapAlignment: (alignment) => mapAlignment(alignment, this.texts()),
          mapLanguage: (language) => mapLanguage(language, this.texts()),
        });
      default:
        return throwError(() => new RulesetEntryNotFoundError(`No section ${sectionId}`));
    }
  }

  /** The texts of the entries, in the reader's language. */
  private texts(): EntryTexts {
    return this.i18n.t().library.entry;
  }

  private refs(resource: string): Observable<ApiRef[]> {
    return listRefs(this.client, VERSION, resource);
  }
}
