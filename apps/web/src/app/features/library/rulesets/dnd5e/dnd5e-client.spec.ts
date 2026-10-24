import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CONTENT_LANGUAGE } from '../content-language';
import { RulesetEntryNotFoundError } from '../ruleset.model';
import { Dnd5eClient } from './dnd5e-client';

describe('Dnd5eClient', () => {
  let client: Dnd5eClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    client = TestBed.inject(Dnd5eClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('gets /api/<version>/<path>', () => {
    let result: unknown;
    client.rest('2014', 'spells/fireball').subscribe((value) => (result = value));
    const request = http.expectOne('https://www.dnd5eapi.co/api/2014/spells/fireball');
    expect(request.request.method).toBe('GET');
    request.flush({ index: 'fireball' });
    expect(result).toEqual({ index: 'fireball' });
  });

  it('posts a GraphQL query to /graphql/<version> and unwraps data', () => {
    let result: unknown;
    client.graphql('2014', 'spells', '{ a }', { limit: 3 }).subscribe((value) => (result = value));
    const request = http.expectOne('https://www.dnd5eapi.co/graphql/2014');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ query: '{ a }', variables: { limit: 3 } });
    request.flush({ data: { a: 1 } });
    expect(result).toEqual({ a: 1 });
  });

  it('fails a GraphQL answer that carries errors or no data', () => {
    const failures: unknown[] = [];
    client.graphql('2014', 'spells', '{ a }').subscribe({ error: (e) => failures.push(e) });
    http.expectOne('https://www.dnd5eapi.co/graphql/2014').flush({ errors: [{ message: 'boom' }] });
    client.graphql('2014', 'spells', '{ b }').subscribe({ error: (e) => failures.push(e) });
    http.expectOne('https://www.dnd5eapi.co/graphql/2014').flush({});
    expect(failures.map((f) => (f as Error).message)).toEqual(['boom', 'Empty GraphQL answer']);
  });

  it('caches a list and does not cache errors', () => {
    const values: unknown[] = [];
    const failures: unknown[] = [];
    const url = 'https://www.dnd5eapi.co/api/2014/classes';

    client.rest('2014', 'classes').subscribe({ error: (e) => failures.push(e) });
    http.expectOne(url).flush('down', { status: 500, statusText: 'Server Error' });
    expect(failures).toHaveLength(1);

    client.rest('2014', 'classes').subscribe((value) => values.push(value));
    http.expectOne(url).flush({ count: 0, results: [] });
    client.rest('2014', 'classes').subscribe((value) => values.push(value));
    http.expectNone(url);
    expect(values).toEqual([
      { count: 0, results: [] },
      { count: 0, results: [] },
    ]);
  });

  it('shares one request between simultaneous callers', () => {
    client.rest('2014', 'races').subscribe();
    client.rest('2014', 'races').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/races').flush({});
  });

  it('caches per version and per GraphQL query', () => {
    client.rest('2014', 'rules').subscribe();
    client.rest('2024', 'rules').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/rules').flush({});
    http.expectOne('https://www.dnd5eapi.co/api/2024/rules').flush({});
    client.graphql('2014', 'spells', '{ a }').subscribe();
    client.graphql('2014', 'spells', '{ b }').subscribe();
    client.graphql('2014', 'spells', '{ a }', { x: 1 }).subscribe();
    const requests = http.match('https://www.dnd5eapi.co/graphql/2014');
    expect(requests).toHaveLength(3);
    requests.forEach((request) => request.flush({ data: {} }));
  });

  it('turns a 404 into a not-found error, other errors stay as they are', () => {
    const failures: unknown[] = [];
    client.rest('2014', 'spells/nope').subscribe({ error: (e) => failures.push(e) });
    http
      .expectOne('https://www.dnd5eapi.co/api/2014/spells/nope')
      .flush({ error: 'Not found' }, { status: 404, statusText: 'Not Found' });
    client.rest('2014', 'spells/down').subscribe({ error: (e) => failures.push(e) });
    http
      .expectOne('https://www.dnd5eapi.co/api/2014/spells/down')
      .flush('x', { status: 503, statusText: 'Unavailable' });
    expect(failures[0]).toBeInstanceOf(RulesetEntryNotFoundError);
    expect(failures[1]).not.toBeInstanceOf(RulesetEntryNotFoundError);
  });

  it('encodes each path segment but keeps the separators', () => {
    client.rest('2014', 'spells/x?y#z').subscribe();
    client.rest('2014', 'classes/wizard/levels').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/spells/x%3Fy%23z').flush({});
    http.expectOne('https://www.dnd5eapi.co/api/2014/classes/wizard/levels').flush({});
  });

  it('fails a GraphQL answer whose data is null', () => {
    const failures: unknown[] = [];
    client.graphql('2014', 'spells', '{ n }').subscribe({ error: (e) => failures.push(e) });
    http.expectOne('https://www.dnd5eapi.co/graphql/2014').flush({ data: null });
    expect(failures).toHaveLength(1);
  });

  it('refuses an empty, "." or ".." segment, encoded or not, without any request', () => {
    const failures: unknown[] = [];
    for (const path of ['spells/..', 'spells/.', 'spells/', 'spells/%2e%2E', 'spells/%2e']) {
      client.rest('2014', path).subscribe({ error: (e) => failures.push(e) });
    }
    expect(failures).toHaveLength(5);
    for (const failure of failures) {
      expect(failure).toBeInstanceOf(RulesetEntryNotFoundError);
    }
    http.expectNone(() => true);
  });
});

describe("Dnd5eClient in the reader's language", () => {
  function setup(language: string) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CONTENT_LANGUAGE, useValue: language },
      ],
    });
    return { client: TestBed.inject(Dnd5eClient), http: TestBed.inject(HttpTestingController) };
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it("asks a French reader's resources in French where the API translates them", () => {
    for (const language of ['fr-FR', 'fr', 'fr-CA']) {
      TestBed.resetTestingModule();
      const { client, http } = setup(language);
      client.rest('2014', 'classes/wizard').subscribe();
      client.rest('2014', 'classes/wizard/levels').subscribe();
      client.graphql('2014', 'spells', '{ a }').subscribe();
      http.expectOne('https://www.dnd5eapi.co/api/2014/classes/wizard?lang=fr-FR').flush({});
      http.expectOne('https://www.dnd5eapi.co/api/2014/classes/wizard/levels?lang=fr-FR').flush([]);
      http.expectOne('https://www.dnd5eapi.co/graphql/2014?lang=fr-FR').flush({ data: {} });
    }
  });

  it('asks in English what the API does not translate, and says it is untranslated', () => {
    const { client, http } = setup('fr-FR');
    client.rest('2014', 'monsters/goblin').subscribe();
    client.rest('2024', 'classes/wizard').subscribe();
    client.graphql('2024', 'spells', '{ a }').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/monsters/goblin').flush({});
    http.expectOne('https://www.dnd5eapi.co/api/2024/classes/wizard').flush({});
    http.expectOne('https://www.dnd5eapi.co/graphql/2024').flush({ data: {} });

    expect(client.untranslated('2014', 'monsters')).toBe(true);
    expect(client.untranslated('2024', 'classes')).toBe(true);
    expect(client.untranslated('2014', 'classes')).toBe(false);
  });

  it('caches each language apart: the French answer is not the English one', () => {
    const { client, http } = setup('fr-FR');
    client.rest('2014', 'spells/fireball').subscribe();
    client.rest('2014', 'spells/fireball').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/spells/fireball?lang=fr-FR').flush({});
  });

  it('translates nothing for an English reader, and nothing is missing for them', () => {
    const { client, http } = setup('en-GB');
    client.rest('2014', 'classes/wizard').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/classes/wizard').flush({});
    expect(client.untranslated('2024', 'classes')).toBe(false);
  });

  it('tells a reader of a language the API does not know that everything is untranslated', () => {
    const { client, http } = setup('de-DE');
    client.rest('2014', 'classes/wizard').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/classes/wizard').flush({});
    expect(client.untranslated('2014', 'classes')).toBe(true);
  });
});
