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
import { ApiBackground, mapBackground } from './mappers/2024/background.mapper';
import { ApiFeat, mapFeat } from './mappers/2024/feat.mapper';
import { ApiMagicItem, mapMagicItem } from './mappers/2024/magic-item.mapper';
import {
  ApiAlignment,
  ApiLanguage,
  mapAlignment,
  mapLanguage,
} from './mappers/2024/personality.mapper';
import { ApiSpecies, ApiSpeciesTrait, mapSpecies } from './mappers/2024/species.mapper';
import { ApiWeaponMastery, mapWeaponMastery } from './mappers/2024/weapon-mastery.mapper';
import {
  ApiClass,
  ApiClassLevel,
  CLASS_FEATURES_QUERIES,
  GqlClassFeatures,
  mapClass,
} from './mappers/class.mapper';
import { ApiRef, EntryTexts } from './mappers/common';
import { ApiMonster, GqlMonster, mapMonster, mapMonsterSummary } from './mappers/monster.mapper';
import { ApiSpell, GqlSpell, mapSpell, mapSpellSummary } from './mappers/spell.mapper';
import { I18n } from '../../../../core/i18n/i18n.service';
import { Dictionary } from '../../../../core/i18n/dictionary';

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

function sections(t: Dictionary): readonly RulesetSection[] {
  const { sections: label, sectionLoading: loading } = t.library;
  return [
    plain(t, 'classes', label.classes, 'players', 'sword', loading.classes),
    plain(t, 'species', label.species, 'players', 'users', loading.peoples),
    plain(t, 'backgrounds', label.backgrounds, 'players', 'book', loading.backgrounds),
    plain(t, 'feats', label.feats, 'players', 'star', loading.feats),
    personalitySection(t),
    spellsSection(t),
    plain(
      t,
      'weapon-mastery-properties',
      label.weaponMasteries,
      'players',
      'sword',
      loading.weaponMasteries,
    ),
    monstersSection(t),
    plain(t, 'magic-items', label.magicItems, 'master', 'crown', loading.magicItems),
  ];
}

/** "DnD 5e — SRD 2024", read from dnd5eapi.co. It has no rules section. */
@Injectable({ providedIn: 'root' })
export class Dnd5e2024Ruleset implements Ruleset {
  readonly id = 'dnd5e-2024';
  readonly label = 'DnD 5e — SRD 2024';
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
    if (sectionId === 'spells') {
      return this.client
        .graphql<{ spells: GqlSpell[] }>(VERSION, 'spells', SPELLS_QUERY)
        .pipe(map((data) => data.spells.map(mapSpellSummary)));
    }
    if (sectionId === 'monsters') {
      return this.client
        .graphql<{ monsters2024: GqlMonster[] }>(VERSION, 'monsters', MONSTERS_QUERY)
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
      case 'species':
        return this.client
          .rest<ApiSpecies>(VERSION, `species/${index}`)
          .pipe(
            switchMap((species) =>
              fetchTraits<ApiSpeciesTrait>(this.client, VERSION, species.traits).pipe(
                map((traits) => mapSpecies(species, traits, this.texts())),
              ),
            ),
          );
      case 'backgrounds':
        return this.client
          .rest<ApiBackground>(VERSION, `backgrounds/${index}`)
          .pipe(map((background) => mapBackground(background, this.texts())));
      case 'feats':
        return this.client
          .rest<ApiFeat>(VERSION, `feats/${index}`)
          .pipe(map((feat) => mapFeat(feat, this.texts())));
      case 'weapon-mastery-properties':
        return this.client
          .rest<ApiWeaponMastery>(VERSION, `weapon-mastery-properties/${index}`)
          .pipe(map((mastery) => mapWeaponMastery(mastery, this.texts())));
      case 'magic-items':
        return this.client
          .rest<ApiMagicItem>(VERSION, `magic-items/${index}`)
          .pipe(map((item) => mapMagicItem(item, this.texts())));
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
