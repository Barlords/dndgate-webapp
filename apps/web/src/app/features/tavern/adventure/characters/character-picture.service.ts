import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, EMPTY, from, map, Observable, shareReplay, switchMap, throwError } from 'rxjs';
import { ApiConfiguration } from '../../../../core/api/generated/api-configuration';
import { getGameCharacterPicture$Png } from '../../../../core/api/generated/functions';
import { toApiProblemAsync } from '../../../../core/errors/api-problem';

interface Entry {
  readers: number;
  url: string | null;
  url$: Observable<string>;
}

/**
 * Character pictures need the access token, so an `<img src>` cannot fetch them: the blob is
 * requested through the API client and shown from an object URL. A picture is fetched once for all
 * the readers present together; the object URL is revoked when the last one leaves.
 */
@Injectable({ providedIn: 'root' })
export class CharacterPictureService {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly entries = new Map<string, Entry>();

  /** Emits the object URL of the picture (and stays open until unsubscribed); fails with an `ApiProblem`. */
  objectUrl(id: string): Observable<string> {
    return new Observable<string>((subscriber) => {
      const entry = this.entries.get(id) ?? this.create(id);
      entry.readers++;
      // Never completes: the reader holds the picture until it unsubscribes.
      const subscription = entry.url$.subscribe({
        next: (url) => subscriber.next(url),
        error: (error: unknown) => subscriber.error(error),
      });
      return () => {
        subscription.unsubscribe();
        entry.readers--;
        if (entry.readers === 0) {
          this.entries.delete(id);
          if (entry.url !== null) {
            URL.revokeObjectURL(entry.url);
          }
        }
      };
    });
  }

  private create(id: string): Entry {
    const entry: Entry = { readers: 0, url: null, url$: EMPTY };
    entry.url$ = getGameCharacterPicture$Png(this.http, this.rootUrl, { id }).pipe(
      map((response) => {
        entry.url = URL.createObjectURL(response.body);
        return entry.url;
      }),
      catchError((error: unknown) =>
        from(toApiProblemAsync(error)).pipe(switchMap((problem) => throwError(() => problem))),
      ),
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    this.entries.set(id, entry);
    return entry;
  }
}
