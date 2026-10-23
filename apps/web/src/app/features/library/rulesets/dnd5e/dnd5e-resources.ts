import { catchError, forkJoin, map, Observable, of, throwError } from 'rxjs';
import { EntryDetail, EntrySummary, RulesetEntryNotFoundError } from '../ruleset.model';
import { Dnd5eClient, Dnd5eVersion } from './dnd5e-client';
import { ApiList, ApiRef } from './mappers/common';
import {
  ALIGNMENT_PREFIX,
  LANGUAGE_PREFIX,
  mapAlignmentSummary,
  mapLanguageSummary,
} from './mappers/personality.mapper';

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

/** The "personality" section merges alignments and languages (same listing in every SRD version). */
export function listPersonality(
  client: Dnd5eClient,
  version: Dnd5eVersion,
): Observable<EntrySummary[]> {
  return forkJoin([
    listRefs(client, version, 'alignments'),
    listRefs(client, version, 'languages'),
  ]).pipe(
    map(([alignments, languages]) => [
      ...alignments.map(mapAlignmentSummary),
      ...languages.map(mapLanguageSummary),
    ]),
  );
}

/** How a version turns the API alignment and language into a detail; the shapes differ. */
export interface PersonalityMappers<A, L> {
  mapAlignment: (alignment: A) => EntryDetail;
  mapLanguage: (language: L) => EntryDetail;
}

/** One entry of the "personality" section: the index prefix says which resource to read. */
export function personalityDetail<A, L>(
  client: Dnd5eClient,
  version: Dnd5eVersion,
  index: string,
  mappers: PersonalityMappers<A, L>,
): Observable<EntryDetail> {
  if (index.startsWith(ALIGNMENT_PREFIX)) {
    return client
      .rest<A>(version, `alignments/${index.slice(ALIGNMENT_PREFIX.length)}`)
      .pipe(map(mappers.mapAlignment));
  }
  if (index.startsWith(LANGUAGE_PREFIX)) {
    return client
      .rest<L>(version, `languages/${index.slice(LANGUAGE_PREFIX.length)}`)
      .pipe(map(mappers.mapLanguage));
  }
  return throwError(() => new RulesetEntryNotFoundError(`No personality entry ${index}`));
}
