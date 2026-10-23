import { catchError, forkJoin, map, Observable, of, throwError } from 'rxjs';
import { RulesetEntryNotFoundError } from '../ruleset.model';
import { Dnd5eClient, Dnd5eVersion } from './dnd5e-client';
import { ApiList, ApiRef } from './mappers/common';

/** The references a REST resource lists. */
export function listRefs(
  client: Dnd5eClient,
  version: Dnd5eVersion,
  resource: string,
): Observable<ApiRef[]> {
  return client.rest<ApiList>(version, resource).pipe(map((list) => list.results));
}

/**
 * The traits a race or species points at, each read from `traits/<index>`. A trait that is gone
 * (404) is left out; any other failure fails the whole read.
 */
export function fetchTraits<T>(
  client: Dnd5eClient,
  version: Dnd5eVersion,
  refs: readonly ApiRef[] | undefined,
): Observable<T[]> {
  if (refs === undefined || refs.length === 0) {
    return of([]);
  }
  return forkJoin(
    refs.map((ref) =>
      client
        .rest<T>(version, `traits/${ref.index}`)
        .pipe(
          catchError((error: unknown) =>
            error instanceof RulesetEntryNotFoundError ? of(null) : throwError(() => error),
          ),
        ),
    ),
  ).pipe(map((traits) => traits.filter((trait): trait is T => trait !== null)));
}
