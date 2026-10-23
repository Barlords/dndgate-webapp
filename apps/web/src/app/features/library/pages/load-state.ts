import { catchError, map, Observable, of, startWith, switchMap } from 'rxjs';

/** What a page shows while it loads: nothing yet (spinner), the value, or a failure (retry). */
export interface LoadState<T> {
  value: T | null;
  failed: boolean;
  /** Why it failed, when it did. */
  error?: unknown;
}

export const LOADING: LoadState<never> = { value: null, failed: false };

/** Runs `load` now and again on every `retries`, each time from the loading state. */
export function loadWithRetry<T>(
  load: () => Observable<T>,
  retries: Observable<void>,
): Observable<LoadState<T>> {
  return retries.pipe(
    startWith(undefined),
    switchMap(() =>
      load().pipe(
        map((value): LoadState<T> => ({ value, failed: false })),
        catchError((error: unknown) => of<LoadState<T>>({ value: null, failed: true, error })),
        startWith(LOADING),
      ),
    ),
  );
}
