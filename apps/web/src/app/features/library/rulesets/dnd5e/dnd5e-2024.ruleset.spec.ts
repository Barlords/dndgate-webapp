import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EntryDetail, EntrySummary, RulesetEntryNotFoundError } from '../ruleset.model';
import { Dnd5e2024Ruleset } from './dnd5e-2024.ruleset';
import alignmentsList from './__fixtures__/2024/list-alignments.json';
import backgroundsList from './__fixtures__/2024/list-backgrounds.json';
import classesList from './__fixtures__/2024/list-classes.json';
import featsList from './__fixtures__/2024/list-feats.json';
import languagesList from './__fixtures__/2024/list-languages.json';
import magicItemsList from './__fixtures__/2024/list-magic-items.json';
import speciesList from './__fixtures__/2024/list-species.json';
import weaponMasteryList from './__fixtures__/2024/list-weapon-mastery-properties.json';
import gqlMonsters from './__fixtures__/2024/gql-monsters.json';
import gqlSpells from './__fixtures__/2024/gql-spells.json';
import acolyte from './__fixtures__/2024/backgrounds-acolyte.json';
import alert from './__fixtures__/2024/feats-alert.json';
import cleave from './__fixtures__/2024/weapon-mastery-cleave.json';
import wizard from './__fixtures__/2024/classes-wizard.json';
import wizardLevels from './__fixtures__/2024/classes-wizard-levels.json';
import wizardFeatures from './__fixtures__/2024/gql-classes-wizard-features.json';
import goblin from './__fixtures__/2024/monsters-goblin-warrior.json';
import bag from './__fixtures__/2024/magic-items-bag-of-holding.json';
import elf from './__fixtures__/2024/species-elf.json';
import human from './__fixtures__/2024/species-human.json';
import alignment from './__fixtures__/2024/alignments-lawful-good.json';
import language from './__fixtures__/2024/languages-common.json';
import fireball from './__fixtures__/2024/spells-fireball.json';

const API = 'https://www.dnd5eapi.co/api/2024';
const GRAPHQL = 'https://www.dnd5eapi.co/graphql/2024';

describe('Dnd5e2024Ruleset', () => {
  let ruleset: Dnd5e2024Ruleset;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    ruleset = TestBed.inject(Dnd5e2024Ruleset);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function listGraphql(sectionId: string, data: object): EntrySummary[] {
    let entries: EntrySummary[] = [];
    ruleset.list(sectionId).subscribe((value) => (entries = value));
    http.expectOne(GRAPHQL).flush(data);
    return entries;
  }

  it('declares its identity', () => {
    expect(ruleset.id).toBe('dnd5e-2024');
    expect(ruleset.label).toBe('DnD 5e — SRD 2024');
    expect(ruleset.spellSectionId).toBe('spells');
  });

  it('exposes species and no rules', () => {
    expect(ruleset.sections.map((s) => [s.id, s.label, s.group])).toEqual([
      ['classes', 'Classes', 'players'],
      ['species', 'Espèces', 'players'],
      ['backgrounds', 'Historiques', 'players'],
      ['feats', 'Dons', 'players'],
      ['personality', 'Personnalité', 'players'],
      ['spells', 'Sorts', 'players'],
      ['weapon-mastery-properties', "Maîtrises d'armes", 'players'],
      ['monsters', 'Monstres', 'master'],
      ['magic-items', 'Objets magiques', 'master'],
    ]);
    expect(ruleset.sections.some((s) => s.id === 'rules' || s.id === 'races')).toBe(false);
    expect(ruleset.sections.some((s) => s.group === 'rules')).toBe(false);
  });

  it('gives every section its own loading flavour', () => {
    const messages = ruleset.sections.map((s) => s.loadingMessage);
    expect(messages.every((message) => message !== undefined && message.endsWith('…'))).toBe(true);
    expect(ruleset.sections.find((s) => s.id === 'spells')?.loadingMessage).toBe(
      'Demande en cours à Mystra…',
    );
    expect(ruleset.sections.find((s) => s.id === 'monsters')?.loadingMessage).toBe(
      'En attente du retour des explorateurs…',
    );
  });

  it('never uses the reserved URL keys for a filter or tabs, and none has an empty list', () => {
    const reserved = ['q', 'sort', 'dir', 'page', 'size'];
    for (const filter of ruleset.sections.flatMap((s) => [
      ...s.filters,
      ...(s.tabs ? [s.tabs] : []),
    ])) {
      expect(reserved).not.toContain(filter.key);
      // Without options, a filter offers the values the entries have.
      expect(filter.options === undefined || filter.options.length > 0).toBe(true);
    }
  });

  it('lists spells from the 2024 endpoint, every value being a filter option', () => {
    const spells = ruleset.sections.find((s) => s.id === 'spells')!;
    const entries = listGraphql('spells', gqlSpells);
    expect(entries.map((e) => e.index)).toEqual(['acid-splash', 'fireball', 'detect-magic']);
    expect(entries[1].fields).toMatchObject({ level: 3, school: 'Evocation' });
    for (const filter of spells.filters.filter((f) => f.options !== undefined)) {
      const values = filter.options!.map((o) => o.value);
      for (const entry of entries) {
        const field = entry.fields[filter.key];
        for (const one of Array.isArray(field) ? field : [String(field)]) {
          expect(values).toContain(one);
        }
      }
    }
  });

  it('lists 2024 monsters', () => {
    const request = (() => {
      ruleset.list('monsters').subscribe();
      return http.expectOne(GRAPHQL);
    })();
    // The 2024 schema names the query `monsters2024`; `monsters` does not exist there.
    expect(request.request.body.query).toContain('monsters2024(limit: 1000)');
    request.flush(gqlMonsters);

    const monsters = ruleset.sections.find((s) => s.id === 'monsters')!;
    ruleset.list('monsters').subscribe((entries) => {
      expect(entries.map((e) => e.index)).toEqual(['goblin-warrior', 'aboleth', 'swarm-of-rats']);
      // "swarm of tiny beasts" is the "swarm" type filter.
      expect(entries.map((e) => e.fields['type'])).toEqual(['fey', 'aberration', 'swarm']);
      expect(entries.map((e) => e.fields['cr'])).toEqual([0.25, 10, 0.25]);
      const types = monsters.filters[0].options!.map((o) => o.value);
      const cr = monsters.filters[1].options!.map((o) => o.value);
      for (const entry of entries) {
        expect(types).toContain(entry.fields['type']);
        expect(cr).toContain(String(entry.fields['cr']));
      }
    });
    http.expectNone(GRAPHQL);
  });

  it('formats the challenge rating column as fractions', () => {
    const cr = ruleset.sections
      .find((s) => s.id === 'monsters')!
      .columns.find((c) => c.key === 'cr')!;
    expect([0, 0.125, 0.25, 0.5, 10].map((v) => cr.format?.(v))).toEqual([
      '0',
      '1/8',
      '1/4',
      '1/2',
      '10',
    ]);
  });

  it('offers only values the 2024 data has', () => {
    const filters = ruleset.sections.flatMap((s) => s.filters);
    // In the language of the data: the names the list has, never a fixed list of English words.
    for (const key of ['school', 'classes', 'castingTime']) {
      expect(filters.find((f) => f.key === key)!.options).toBeUndefined();
    }
    const cr = filters.find((f) => f.key === 'cr')!.options!.map((o) => o.value);
    expect(cr).not.toContain('18');
    expect(cr).toHaveLength(28);
  });

  it('lists the REST sections from their resource', () => {
    const lists: [string, string, { results: { index: string }[] }][] = [
      ['classes', 'classes', classesList],
      ['species', 'species', speciesList],
      ['backgrounds', 'backgrounds', backgroundsList],
      ['feats', 'feats', featsList],
      ['weapon-mastery-properties', 'weapon-mastery-properties', weaponMasteryList],
      ['magic-items', 'magic-items', magicItemsList],
    ];
    for (const [section, resource, body] of lists) {
      let entries: EntrySummary[] = [];
      ruleset.list(section).subscribe((value) => (entries = value));
      http.expectOne(`${API}/${resource}`).flush(body);
      expect(entries.map((e) => e.index)).toEqual(body.results.map((r) => r.index));
      expect(entries[0].fields).toEqual({});
    }
  });

  it('merges alignments and languages in the personality section', () => {
    let entries: EntrySummary[] = [];
    ruleset.list('personality').subscribe((value) => (entries = value));
    http.expectOne(`${API}/alignments`).flush(alignmentsList);
    http.expectOne(`${API}/languages`).flush(languagesList);
    expect(entries).toHaveLength(alignmentsList.results.length + languagesList.results.length);
    expect(entries[0].fields['kind']).toBe('Alignment');
    expect(entries.at(-1)?.fields['kind']).toBe('Language');
    expect(entries[0].index).toBe(`alignment.${alignmentsList.results[0].index}`);
  });

  it('lists nothing for an unknown section, nor for the 2014-only ones', () => {
    for (const section of ['nope', 'races', 'rules']) {
      let entries: EntrySummary[] | null = null;
      ruleset.list(section).subscribe((value) => (entries = value));
      expect(entries).toEqual([]);
    }
  });

  describe('detail', () => {
    function detailOf(section: string, index: string) {
      let result: EntryDetail | null = null;
      ruleset.detail(section, index).subscribe((value) => (result = value));
      return () => result;
    }

    it('reads a spell with its 2024 description', () => {
      const result = detailOf('spells', 'fireball');
      http.expectOne(`${API}/spells/fireball`).flush(fireball);
      expect(result()?.subtitle).toBe('3rd-level evocation');
    });

    it('reads a monster, a class with its levels, a background, a feat and a mastery', () => {
      const monster = detailOf('monsters', 'goblin-warrior');
      http.expectOne(`${API}/monsters/goblin-warrior`).flush(goblin);
      expect(monster()?.name).toBe('Goblin Warrior');

      const cls = detailOf('classes', 'wizard');
      http.expectOne(`${API}/classes/wizard`).flush(wizard);
      http.expectOne(`${API}/classes/wizard/levels`).flush(wizardLevels);
      const features = http.expectOne(GRAPHQL);
      expect(features.request.body.variables).toEqual({ class: 'wizard' });
      features.flush(wizardFeatures);
      expect(cls()?.blocks.some((b) => b.kind === 'table')).toBe(true);
      expect(cls()?.blocks.map((b) => b.title)).toEqual(
        expect.arrayContaining(['Class features', 'Subclass: Evoker', 'Evoker features']),
      );

      const background = detailOf('backgrounds', 'acolyte');
      http.expectOne(`${API}/backgrounds/acolyte`).flush(acolyte);
      expect(background()?.name).toBe('Acolyte');

      const feat = detailOf('feats', 'alert');
      http.expectOne(`${API}/feats/alert`).flush(alert);
      expect(feat()?.subtitle).toBe('Origin feat');

      const mastery = detailOf('weapon-mastery-properties', 'cleave');
      http.expectOne(`${API}/weapon-mastery-properties/cleave`).flush(cleave);
      expect(mastery()?.name).toBe('Cleave');
    });

    it('reads a magic item whose index holds parentheses', () => {
      const result = detailOf('magic-items', 'stone-of-good-luck-(luckstone)');
      http.expectOne(`${API}/magic-items/stone-of-good-luck-(luckstone)`).flush(bag);
      expect(result()?.name).toBe('Bag of Holding');
    });

    it('reads a species with its traits, leaving out one that is gone', () => {
      const result = detailOf('species', 'elf');
      http.expectOne(`${API}/species/elf`).flush(elf);
      for (const trait of elf.traits) {
        const request = http.expectOne(`${API}/traits/${trait.index}`);
        if (trait.index === 'trance') {
          request.flush({ error: 'Not found' }, { status: 404, statusText: 'Not Found' });
        } else {
          request.flush({ index: trait.index, name: trait.name, description: 'Text.' });
        }
      }
      const traits = result()?.blocks.find((b) => b.kind === 'entries');
      expect(traits?.kind === 'entries' && traits.items.map((i) => i.name)).toEqual([
        'Darkvision (60 ft.)',
        'Elven Lineage',
        'Fey Ancestry',
        'Keen Senses',
      ]);
    });

    it('fails the whole species when a trait fails for another reason than 404', () => {
      const failures: unknown[] = [];
      ruleset.detail('species', 'human').subscribe({ error: (e) => failures.push(e) });
      http.expectOne(`${API}/species/human`).flush(human);
      human.traits.forEach((trait, i) => {
        const request = http.expectOne(`${API}/traits/${trait.index}`);
        if (i === 0) {
          request.flush('x', { status: 500, statusText: 'Error' });
        } else {
          request.flush({ index: trait.index, name: trait.name, description: 'Text.' });
        }
      });
      expect(failures).toHaveLength(1);
      expect(failures[0]).not.toBeInstanceOf(RulesetEntryNotFoundError);
    });

    it('reads a species that has no traits without further requests', () => {
      const result = detailOf('species', 'dragonborn');
      http.expectOne(`${API}/species/dragonborn`).flush({
        index: 'dragonborn',
        name: 'Dragonborn',
        type: 'Humanoid',
        size: 'Medium',
        speed: 30,
      });
      expect(result()?.name).toBe('Dragonborn');
    });

    it('routes a personality entry to its resource by prefix', () => {
      const a = detailOf('personality', 'alignment.lawful-good');
      http.expectOne(`${API}/alignments/lawful-good`).flush(alignment);
      expect(a()?.subtitle).toBe('Alignment');
      const l = detailOf('personality', 'language.common');
      http.expectOne(`${API}/languages/common`).flush(language);
      expect(l()?.subtitle).toBe('Language');
    });

    it('fails with a not-found error for a 404, an unknown prefix or a 2014-only section', () => {
      const failures: unknown[] = [];
      const watch = (section: string, index: string) =>
        ruleset.detail(section, index).subscribe({ error: (e) => failures.push(e) });
      watch('spells', 'nope');
      http
        .expectOne(`${API}/spells/nope`)
        .flush({ error: 'Not found' }, { status: 404, statusText: 'Not Found' });
      watch('personality', 'weird');
      watch('rules', 'combat');
      watch('races', 'dwarf');
      expect(failures).toHaveLength(4);
      for (const failure of failures) {
        expect(failure).toBeInstanceOf(RulesetEntryNotFoundError);
      }
    });

    it('sends no request for an index that would climb the path', () => {
      const failures: unknown[] = [];
      for (const index of ['..', '.']) {
        ruleset.detail('feats', index).subscribe({ error: (e) => failures.push(e) });
      }
      expect(failures).toHaveLength(2);
      http.expectNone(() => true);
    });

    it('keeps a server failure distinct from not-found', () => {
      const failures: unknown[] = [];
      ruleset.detail('spells', 'fireball').subscribe({ error: (e) => failures.push(e) });
      http
        .expectOne(`${API}/spells/fireball`)
        .flush('x', { status: 500, statusText: 'Server Error' });
      expect(failures[0]).not.toBeInstanceOf(RulesetEntryNotFoundError);
    });
  });
});
