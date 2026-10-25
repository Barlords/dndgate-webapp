import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, shareReplay, throwError } from 'rxjs';
import { RulesetEntryNotFoundError } from '../ruleset.model';
import { I18n } from '../../../../core/i18n/i18n.service';
import { Language } from '../../../../core/i18n/dictionary';

export type Dnd5eVersion = '2014' | '2024';

/**
 * What dnd5eapi.co translates (`?lang=`), by version, language and resource: it answers in English,
 * without a word, for anything else. Read from the API in October 2026 (each resource asked with
 * and without `?lang=`): to update when it translates more.
 */
const TRANSLATED: Record<Dnd5eVersion, Partial<Record<string, ReadonlySet<string>>>> = {
  '2014': {
    'fr-FR': new Set([
      'spells',
      'classes',
      'features',
      'races',
      'subraces',
      'traits',
      'backgrounds',
      'rules',
      'alignments',
      'languages',
      'proficiencies',
      'skills',
      'ability-scores',
      'magic-schools',
      'equipment',
    ]),
  },
  '2024': {},
};

/** The language codes the API knows, by language of the interface; English is its own. */
const API_LANGUAGES: Record<Language, string | null> = { fr: 'fr-FR', en: null };

/** The API's own language, what it answers in for what it does not translate. */
const SOURCE_LANGUAGE = 'en';

interface GraphqlResponse<T> {
  data?: T;
  errors?: { message: string }[];
}

/**
 * Whether a path segment is empty, `.` or `..`, also once percent-decoded: URL parsers treat
 * `%2E%2E` as `..`, so such a segment would climb out of the resource it was meant for.
 */
function climbs(segment: string): boolean {
  let decoded = segment;
  try {
    decoded = decodeURIComponent(segment);
  } catch {
    // Not valid percent-encoding: it is then encoded as is, and cannot climb.
  }
  return decoded === '' || decoded === '.' || decoded === '..';
}

/**
 * The only door to dnd5eapi.co. Answers are cached in memory for the life of the page, by URL (REST)
 * or by query and variables (GraphQL); a failure is never cached, so asking again retries. A
 * resource the API translates into the reader's language (the interface's) is asked in it
 * (`?lang=`), anything else in English.
 */
@Injectable({ providedIn: 'root' })
export class Dnd5eClient {
  readonly baseUrl = 'https://www.dnd5eapi.co';

  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Observable<unknown>>();
  private readonly i18n = inject(I18n);

  /** The language to ask `resource` in, when the API translates it into the reader's. */
  private translation(version: Dnd5eVersion, resource: string): string | null {
    const language = API_LANGUAGES[this.i18n.language()];
    return language !== null && TRANSLATED[version][language]?.has(resource) ? language : null;
  }

  /** Whether the reader reads another language than English and `resource` is not in it. */
  untranslated(version: Dnd5eVersion, resource: string): boolean {
    return this.i18n.language() !== 'en' && this.translation(version, resource) === null;
  }

  /**
   * Always names the language: without `?lang=`, the API answers in the browser's
   * (`Accept-Language`), which may not be the interface's.
   */
  private withLanguage(url: string, version: Dnd5eVersion, resource: string): string {
    return `${url}?lang=${this.translation(version, resource) ?? SOURCE_LANGUAGE}`;
  }

  /**
   * GET `/api/<version>/<path>`, in the reader's language when the API translates the resource (the
   * first segment of the path); a 404 fails with `RulesetEntryNotFoundError`.
   */
  rest<T>(version: Dnd5eVersion, path: string): Observable<T> {
    const segments = path.split('/');
    if (segments.some(climbs)) {
      return throwError(() => new RulesetEntryNotFoundError(`Invalid resource path: ${path}`));
    }
    const url = this.withLanguage(
      `${this.baseUrl}/api/${version}/${segments.map(encodeURIComponent).join('/')}`,
      version,
      segments[0],
    );
    return this.cached(url, () =>
      this.http
        .get<T>(url)
        .pipe(
          catchError((error: unknown) =>
            throwError(() =>
              error instanceof HttpErrorResponse && error.status === 404
                ? new RulesetEntryNotFoundError(`No such resource: ${path}`)
                : error,
            ),
          ),
        ),
    );
  }

  /**
   * POST `/graphql/<version>`, in the reader's language when the API translates `resource` (what the
   * query reads, e.g. `spells`); resolves with the `data` of the answer.
   */
  graphql<T>(
    version: Dnd5eVersion,
    resource: string,
    query: string,
    variables?: object,
  ): Observable<T> {
    const url = this.withLanguage(`${this.baseUrl}/graphql/${version}`, version, resource);
    const key = `${url}\n${query}\n${JSON.stringify(variables ?? null)}`;
    return this.cached(key, () =>
      this.http.post<GraphqlResponse<T>>(url, { query, variables }).pipe(
        map((response) => {
          if (response.data == null || (response.errors?.length ?? 0) > 0) {
            throw new Error(response.errors?.[0]?.message ?? 'Empty GraphQL answer');
          }
          return response.data;
        }),
      ),
    );
  }

  private cached<T>(key: string, load: () => Observable<T>): Observable<T> {
    const hit = this.cache.get(key);
    if (hit) {
      return hit as Observable<T>;
    }
    // `shareReplay` resets on error, so a failed request is made again by the next caller.
    const shared = load().pipe(shareReplay({ bufferSize: 1, refCount: false }));
    this.cache.set(key, shared);
    return shared;
  }
}
