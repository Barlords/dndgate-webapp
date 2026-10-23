import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EntryDetail, EntrySummary, RulesetEntryNotFoundError } from '../ruleset.model';
import { Dnd5e2014Ruleset } from './dnd5e-2014.ruleset';
import alignmentsList from './__fixtures__/2014/list-alignments.json';
import backgroundsList from './__fixtures__/2014/list-backgrounds.json';
import classesList from './__fixtures__/2014/list-classes.json';
import languagesList from './__fixtures__/2014/list-languages.json';
import racesList from './__fixtures__/2014/list-races.json';
import rulesList from './__fixtures__/2014/list-rules.json';
import gqlMonsters from './__fixtures__/2014/gql-monsters.json';
import gqlSpells from './__fixtures__/2014/gql-spells.json';
import acolyte from './__fixtures__/2014/backgrounds-acolyte.json';
import alignment from './__fixtures__/2014/alignments-lawful-good.json';
import wizard from './__fixtures__/2014/classes-wizard.json';
import wizardLevels from './__fixtures__/2014/classes-wizard-levels.json';
import language from './__fixtures__/2014/languages-common.json';
import goblin from './__fixtures__/2014/monsters-goblin.json';
import dwarf from './__fixtures__/2014/races-dwarf.json';
import combat from './__fixtures__/2014/rules-combat.json';
import fireball from './__fixtures__/2014/spells-fireball.json';

const API = 'https://www.dnd5eapi.co/api/2014';
const GRAPHQL = 'https://www.dnd5eapi.co/graphql/2014';

describe('Dnd5e2014Ruleset', () => {
  let ruleset: Dnd5e2014Ruleset;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    ruleset = TestBed.inject(Dnd5e2014Ruleset);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function listed(sectionId: string): EntrySummary[] {
    let entries: EntrySummary[] = [];
    ruleset.list(sectionId).subscribe((value) => (entries = value));
    return entries;
  }

  /** Subscribes, answers the GraphQL request and returns what the ruleset listed. */
  function listGraphql(sectionId: string, data: object): EntrySummary[] {
    let entries: EntrySummary[] = [];
    ruleset.list(sectionId).subscribe((value) => (entries = value));
    http.expectOne(GRAPHQL).flush(data);
    return entries;
  }

  it('declares its identity and the sections of the brief', () => {
    expect(ruleset.id).toBe('dnd5e-2014');
    expect(ruleset.label).toBe('DnD 5e — SRD 2014');
    expect(ruleset.spellSectionId).toBe('spells');
    expect(ruleset.sections.map((s) => [s.id, s.label, s.group])).toEqual([
      ['classes', 'Classes', 'players'],
      ['races', 'Races', 'players'],
      ['backgrounds', 'Historiques', 'players'],
      ['personality', 'Personnalité', 'players'],
      ['spells', 'Sorts', 'players'],
      ['monsters', 'Monstres', 'master'],
      ['rules', 'Règles', 'rules'],
    ]);
  });

  it('never uses the reserved URL keys for a filter, and every filter has options', () => {
    const reserved = ['q', 'sort', 'dir', 'page', 'size'];
    for (const filter of ruleset.sections.flatMap((s) => s.filters)) {
      expect(reserved).not.toContain(filter.key);
      expect(filter.options.length).toBeGreaterThan(0);
    }
  });

  it('lists spells with level, school and classes filters', () => {
    const spells = ruleset.sections.find((s) => s.id === 'spells')!;
    expect(spells.filters.map((f) => [f.key, f.label])).toEqual([
      ['level', 'Niveau'],
      ['school', 'École'],
      ['classes', 'Classe'],
    ]);
    expect(spells.filters[0].options[0]).toEqual({ value: '0', label: 'Tour de magie' });
    expect(spells.filters[0].options[3]).toEqual({ value: '3', label: 'Niveau 3' });
    expect(spells.columns.map((c) => c.key)).toEqual(['name', 'level', 'school', 'classes']);

    const entries = listGraphql('spells', gqlSpells);
    expect(entries.map((e) => e.index)).toEqual(['acid-splash', 'fireball', 'detect-magic']);
    expect(entries[1].fields).toMatchObject({ level: 3, school: 'Evocation' });
    // Every spell's level, school and class is one of the filter options.
    for (const filter of spells.filters) {
      const values = filter.options.map((o) => o.value);
      for (const entry of entries) {
        const field = entry.fields[filter.key];
        for (const one of Array.isArray(field) ? field : [String(field)]) {
          expect(values).toContain(one);
        }
      }
    }
  });

  it('sends the spells query once and asks for every spell', () => {
    ruleset.list('spells').subscribe();
    const request = http.expectOne(GRAPHQL);
    expect(request.request.body.query).toContain('limit: 1000');
    request.flush(gqlSpells);
    ruleset.list('spells').subscribe();
    http.expectNone(GRAPHQL);
  });

  it('monsters filter by challenge rating', () => {
    const monsters = ruleset.sections.find((s) => s.id === 'monsters')!;
    expect(monsters.filters.map((f) => [f.key, f.label])).toEqual([
      ['type', 'Type'],
      ['cr', 'FP'],
    ]);
    expect(monsters.filters[1].options.slice(0, 5)).toEqual([
      { value: '0', label: '0' },
      { value: '0.125', label: '1/8' },
      { value: '0.25', label: '1/4' },
      { value: '0.5', label: '1/2' },
      { value: '1', label: '1' },
    ]);
    const entries = listGraphql('monsters', gqlMonsters);
    expect(entries.map((e) => e.fields['cr'])).toEqual([0.25, 17, 0.25]);
    const cr = monsters.filters[1].options.map((o) => o.value);
    for (const entry of entries) {
      expect(cr).toContain(String(entry.fields['cr']));
    }
    expect(monsters.filters[0].options.map((o) => o.value)).toContain('dragon');
  });

  it('lists the REST sections from their resource', () => {
    const lists: [string, string, { results: { index: string }[] }][] = [
      ['classes', 'classes', classesList],
      ['races', 'races', racesList],
      ['backgrounds', 'backgrounds', backgroundsList],
      ['rules', 'rules', rulesList],
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
    expect(entries).toHaveLength(6);
    expect(entries.map((e) => e.fields['kind'])).toEqual([
      'Alignment',
      'Alignment',
      'Alignment',
      'Language',
      'Language',
      'Language',
    ]);
    expect(entries[0].index).toBe(`alignment.${alignmentsList.results[0].index}`);
  });

  it('lists nothing for an unknown section', () => {
    expect(listed('nope')).toEqual([]);
  });

  describe('detail', () => {
    function detailOf(section: string, index: string) {
      let result: EntryDetail | null = null;
      ruleset.detail(section, index).subscribe((value) => (result = value));
      return () => result;
    }

    it('reads a spell', () => {
      const result = detailOf('spells', 'fireball');
      http.expectOne(`${API}/spells/fireball`).flush(fireball);
      expect(result()?.subtitle).toBe('3rd-level evocation');
    });

    it('reads a monster', () => {
      const result = detailOf('monsters', 'goblin');
      http.expectOne(`${API}/monsters/goblin`).flush(goblin);
      expect(result()?.name).toBe('Goblin');
    });

    it('reads a class with its levels', () => {
      const result = detailOf('classes', 'wizard');
      http.expectOne(`${API}/classes/wizard`).flush(wizard);
      http.expectOne(`${API}/classes/wizard/levels`).flush(wizardLevels);
      expect(result()?.blocks.some((b) => b.kind === 'table')).toBe(true);
    });

    it('reads a race with its traits, leaving out one that fails', () => {
      const result = detailOf('races', 'dwarf');
      http.expectOne(`${API}/races/dwarf`).flush(dwarf);
      for (const trait of dwarf.traits) {
        const request = http.expectOne(`${API}/traits/${trait.index}`);
        if (trait.index === 'stonecunning') {
          request.flush('x', { status: 500, statusText: 'Error' });
        } else {
          request.flush({ index: trait.index, name: trait.name, desc: ['Text.'] });
        }
      }
      const traits = result()?.blocks.find((b) => b.kind === 'entries');
      expect(traits?.kind === 'entries' && traits.items.map((i) => i.name)).toEqual([
        'Darkvision',
        'Dwarven Resilience',
        'Dwarven Combat Training',
        'Tool Proficiency',
      ]);
    });

    it('reads a race without traits without further requests', () => {
      const result = detailOf('races', 'ghost');
      http.expectOne(`${API}/races/ghost`).flush({ ...dwarf, traits: [] });
      expect(result()?.blocks.some((b) => b.kind === 'entries')).toBe(false);
    });

    it('reads a background and a rule', () => {
      const background = detailOf('backgrounds', 'acolyte');
      http.expectOne(`${API}/backgrounds/acolyte`).flush(acolyte);
      expect(background()?.name).toBe('Acolyte');
      const rule = detailOf('rules', 'combat');
      http.expectOne(`${API}/rules/combat`).flush(combat);
      expect(rule()?.name).toBe('Combat');
    });

    it('routes a personality entry to its resource by prefix', () => {
      const a = detailOf('personality', 'alignment.lawful-good');
      http.expectOne(`${API}/alignments/lawful-good`).flush(alignment);
      expect(a()?.subtitle).toBe('Alignment');
      const l = detailOf('personality', 'language.common');
      http.expectOne(`${API}/languages/common`).flush(language);
      expect(l()?.subtitle).toBe('Language');
    });

    it('fails with a not-found error for a 404, an unknown prefix or an unknown section', () => {
      const failures: unknown[] = [];
      const watch = (section: string, index: string) =>
        ruleset.detail(section, index).subscribe({ error: (e) => failures.push(e) });
      watch('spells', 'nope');
      http
        .expectOne(`${API}/spells/nope`)
        .flush({ error: 'Not found' }, { status: 404, statusText: 'Not Found' });
      watch('personality', 'weird');
      watch('nope', 'x');
      expect(failures).toHaveLength(3);
      for (const failure of failures) {
        expect(failure).toBeInstanceOf(RulesetEntryNotFoundError);
      }
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
