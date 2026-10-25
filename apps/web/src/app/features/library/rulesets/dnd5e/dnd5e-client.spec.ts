import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RulesetEntryNotFoundError } from '../ruleset.model';
import { Dnd5eClient } from './dnd5e-client';
import { I18n } from '../../../../core/i18n/i18n.service';
import { Language } from '../../../../core/i18n/dictionary';

describe('Dnd5eClient', () => {
  let client: Dnd5eClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    // The data and the texts as the API writes them: an English reader asks no translation.
    TestBed.inject(I18n).use('en');
    client = TestBed.inject(Dnd5eClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('gets /api/<version>/<path>', () => {
    let result: unknown;
    client.rest('2014', 'spells/fireball').subscribe((value) => (result = value));
    const request = http.expectOne('https://www.dnd5eapi.co/api/2014/spells/fireball?lang=en');
    expect(request.request.method).toBe('GET');
    request.flush({ index: 'fireball' });
    expect(result).toEqual({ index: 'fireball' });
  });

  it('posts a GraphQL query to /graphql/<version> and unwraps data', () => {
    let result: unknown;
    client.graphql('2014', 'spells', '{ a }', { limit: 3 }).subscribe((value) => (result = value));
    const request = http.expectOne('https://www.dnd5eapi.co/graphql/2014?lang=en');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ query: '{ a }', variables: { limit: 3 } });
    request.flush({ data: { a: 1 } });
    expect(result).toEqual({ a: 1 });
  });

  it('fails a GraphQL answer that carries errors or no data', () => {
    const failures: unknown[] = [];
    client.graphql('2014', 'spells', '{ a }').subscribe({ error: (e) => failures.push(e) });
    http
      .expectOne('https://www.dnd5eapi.co/graphql/2014?lang=en')
      .flush({ errors: [{ message: 'boom' }] });
    client.graphql('2014', 'spells', '{ b }').subscribe({ error: (e) => failures.push(e) });
    http.expectOne('https://www.dnd5eapi.co/graphql/2014?lang=en').flush({});
    expect(failures.map((f) => (f as Error).message)).toEqual(['boom', 'Empty GraphQL answer']);
  });

  it('caches a list and does not cache errors', () => {
    const values: unknown[] = [];
    const failures: unknown[] = [];
    const url = 'https://www.dnd5eapi.co/api/2014/classes?lang=en';

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
    http.expectOne('https://www.dnd5eapi.co/api/2014/races?lang=en').flush({});
  });

  it('caches per version and per GraphQL query', () => {
    client.rest('2014', 'rules').subscribe();
    client.rest('2024', 'rules').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/rules?lang=en').flush({});
    http.expectOne('https://www.dnd5eapi.co/api/2024/rules?lang=en').flush({});
    client.graphql('2014', 'spells', '{ a }').subscribe();
    client.graphql('2014', 'spells', '{ b }').subscribe();
    client.graphql('2014', 'spells', '{ a }', { x: 1 }).subscribe();
    const requests = http.match('https://www.dnd5eapi.co/graphql/2014?lang=en');
    expect(requests).toHaveLength(3);
    requests.forEach((request) => request.flush({ data: {} }));
  });

  it('turns a 404 into a not-found error, other errors stay as they are', () => {
    const failures: unknown[] = [];
    client.rest('2014', 'spells/nope').subscribe({ error: (e) => failures.push(e) });
    http
      .expectOne('https://www.dnd5eapi.co/api/2014/spells/nope?lang=en')
      .flush({ error: 'Not found' }, { status: 404, statusText: 'Not Found' });
    client.rest('2014', 'spells/down').subscribe({ error: (e) => failures.push(e) });
    http
      .expectOne('https://www.dnd5eapi.co/api/2014/spells/down?lang=en')
      .flush('x', { status: 503, statusText: 'Unavailable' });
    expect(failures[0]).toBeInstanceOf(RulesetEntryNotFoundError);
    expect(failures[1]).not.toBeInstanceOf(RulesetEntryNotFoundError);
  });

  it('encodes each path segment but keeps the separators', () => {
    client.rest('2014', 'spells/x?y#z').subscribe();
    client.rest('2014', 'classes/wizard/levels').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/spells/x%3Fy%23z?lang=en').flush({});
    http.expectOne('https://www.dnd5eapi.co/api/2014/classes/wizard/levels?lang=en').flush({});
  });

  it('fails a GraphQL answer whose data is null', () => {
    const failures: unknown[] = [];
    client.graphql('2014', 'spells', '{ n }').subscribe({ error: (e) => failures.push(e) });
    http.expectOne('https://www.dnd5eapi.co/graphql/2014?lang=en').flush({ data: null });
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
  function setup(language: Language) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(I18n).use(language);
    return { client: TestBed.inject(Dnd5eClient), http: TestBed.inject(HttpTestingController) };
  }

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    localStorage.clear();
  });

  it("asks a French reader's resources in French where the API translates them", () => {
    const { client, http } = setup('fr');
    client.rest('2014', 'classes/wizard').subscribe();
    client.rest('2014', 'classes/wizard/levels').subscribe();
    client.graphql('2014', 'spells', '{ a }').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/classes/wizard?lang=fr-FR').flush({});
    http.expectOne('https://www.dnd5eapi.co/api/2014/classes/wizard/levels?lang=fr-FR').flush([]);
    http.expectOne('https://www.dnd5eapi.co/graphql/2014?lang=fr-FR').flush({ data: {} });
  });

  it('asks in English what the API does not translate, and says it is untranslated', () => {
    const { client, http } = setup('fr');
    client.rest('2014', 'monsters/goblin').subscribe();
    client.rest('2024', 'classes/wizard').subscribe();
    client.graphql('2024', 'spells', '{ a }').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/monsters/goblin?lang=en').flush({});
    http.expectOne('https://www.dnd5eapi.co/api/2024/classes/wizard?lang=en').flush({});
    http.expectOne('https://www.dnd5eapi.co/graphql/2024?lang=en').flush({ data: {} });

    expect(client.untranslated('2014', 'monsters')).toBe(true);
    expect(client.untranslated('2024', 'classes')).toBe(true);
    expect(client.untranslated('2014', 'classes')).toBe(false);
  });

  it('caches each language apart, and follows a change of language', () => {
    const { client, http } = setup('fr');
    client.rest('2014', 'spells/fireball').subscribe();
    client.rest('2014', 'spells/fireball').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/spells/fireball?lang=fr-FR').flush({});

    TestBed.inject(I18n).use('en');
    client.rest('2014', 'spells/fireball').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/spells/fireball?lang=en').flush({});
  });

  // Without `?lang=`, the API answers in the browser's language (`Accept-Language`), whatever the
  // interface's: every request names its language.
  it('asks an English reader in English, even where the API speaks French', () => {
    const { client, http } = setup('en');
    client.rest('2014', 'classes/wizard').subscribe();
    client.graphql('2014', 'spells', '{ a }').subscribe();
    http.expectOne('https://www.dnd5eapi.co/api/2014/classes/wizard?lang=en').flush({});
    http.expectOne('https://www.dnd5eapi.co/graphql/2014?lang=en').flush({ data: {} });
    expect(client.untranslated('2024', 'classes')).toBe(false);
  });
});
