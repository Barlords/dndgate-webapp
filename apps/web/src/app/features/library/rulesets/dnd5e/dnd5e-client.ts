import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, shareReplay, throwError } from 'rxjs';
import { RulesetEntryNotFoundError } from '../ruleset.model';

export type Dnd5eVersion = '2014' | '2024';

interface GraphqlResponse<T> {
  data?: T;
  errors?: { message: string }[];
}

/**
 * The only door to dnd5eapi.co. Answers are cached in memory for the life of the page, by URL (REST)
 * or by query and variables (GraphQL); a failure is never cached, so asking again retries.
 */
@Injectable({ providedIn: 'root' })
export class Dnd5eClient {
  readonly baseUrl = 'https://www.dnd5eapi.co';

  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Observable<unknown>>();

  /** GET `/api/<version>/<path>`; a 404 fails with `RulesetEntryNotFoundError`. */
  rest<T>(version: Dnd5eVersion, path: string): Observable<T> {
    const url = `${this.baseUrl}/api/${version}/${path}`;
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

  /** POST `/graphql/<version>`; resolves with the `data` of the answer. */
  graphql<T>(version: Dnd5eVersion, query: string, variables?: object): Observable<T> {
    const url = `${this.baseUrl}/graphql/${version}`;
    const key = `${url}\n${query}\n${JSON.stringify(variables ?? null)}`;
    return this.cached(key, () =>
      this.http.post<GraphqlResponse<T>>(url, { query, variables }).pipe(
        map((response) => {
          if (response.data === undefined || (response.errors?.length ?? 0) > 0) {
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
