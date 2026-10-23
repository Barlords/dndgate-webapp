import { Observable, expand, map, reduce } from 'rxjs';

/** Page size to request from every paginated endpoint of the API. */
export const PAGE_SIZE = 100;

interface PageLike<T> {
  content?: T[];
  totalPages?: number;
}

/**
 * Loads every page of a paginated endpoint (page 0 first) and concatenates the items in order.
 * `load` receives the page index and must request `PAGE_SIZE` items.
 */
export function fetchAllPages<T>(
  load: (page: number) => Observable<PageLike<T>>,
): Observable<T[]> {
  return load(0).pipe(
    expand((current, index) => (index + 1 < (current.totalPages ?? 1) ? load(index + 1) : [])),
    map((current) => current.content ?? []),
    reduce((all, items) => all.concat(items), [] as T[]),
  );
}
