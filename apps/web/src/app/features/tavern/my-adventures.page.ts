import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, Subject, switchMap } from 'rxjs';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { getAdventuresAsMaster, getAdventuresAsPlayer } from '../../core/api/generated/functions';
import { AdventureDto } from '../../core/api/generated/models/adventure-dto';
import { fetchAllPages, PAGE_SIZE } from '../../core/api/fetch-all-pages';
import { Button } from '../../shared/ui/button/button';
import { Parchment } from '../../shared/ui/parchment/parchment';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { AdventureCard } from './adventure-card';
import { isClosed, Status } from './adventure-status';

interface Mine {
  adventure: AdventureDto;
  role: 'MJ' | 'Joueur';
}

type Loaded = { state: 'loading' } | { state: 'failed' } | { state: 'ready'; all: Mine[] };

/** The adventures the reader plays or runs, on the « En cours » or the « Terminées » tab. */
@Component({
  selector: 'dg-my-adventures-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, AdventureCard, Button, Spinner],
  styleUrl: './tavern-page.scss',
  template: `
    <dg-parchment>
      <h1>{{ closed ? 'Aventures terminées' : 'Aventures en cours' }}</h1>
      @let current = loaded();
      @if (current.state === 'ready') {
        @if (shown().length > 0) {
          <ul class="cards">
            @for (item of shown(); track item.adventure.id) {
              <li><dg-adventure-card [adventure]="item.adventure" [role]="item.role" /></li>
            }
          </ul>
        } @else {
          <p>{{ closed ? 'Aucune aventure terminée.' : 'Aucune aventure en cours.' }}</p>
        }
      } @else if (current.state === 'failed') {
        <div class="state">
          <p role="alert">Impossible de charger tes aventures.</p>
          <dg-button variant="ghost" (click)="retries.next()">Réessayer</dg-button>
        </div>
      } @else {
        <div class="state"><dg-spinner label="Chargement des aventures" /></div>
      }
    </dg-parchment>
  `,
})
export class MyAdventuresPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;

  /** `true` on the « Terminées » tab (route data). */
  protected readonly closed = inject(ActivatedRoute).snapshot.data['closed'] === true;
  protected readonly retries = new Subject<void>();
  protected readonly loaded = signal<Loaded>({ state: 'loading' });

  protected readonly shown = computed<Mine[]>(() => {
    const current = this.loaded();
    return current.state === 'ready'
      ? current.all.filter((item) => isClosed(item.adventure.status as Status) === this.closed)
      : [];
  });

  constructor() {
    this.retries
      .pipe(
        startWith(undefined),
        switchMap(() =>
          forkJoin({
            player: fetchAllPages((page) =>
              getAdventuresAsPlayer(this.http, this.rootUrl, { page, size: PAGE_SIZE }).pipe(
                map((response) => response.body),
              ),
            ),
            master: fetchAllPages((page) =>
              getAdventuresAsMaster(this.http, this.rootUrl, { page, size: PAGE_SIZE }).pipe(
                map((response) => response.body),
              ),
            ),
          }).pipe(
            map(({ player, master }): Loaded => ({ state: 'ready', all: merge(player, master) })),
            catchError(() => of<Loaded>({ state: 'failed' })),
            startWith<Loaded>({ state: 'loading' }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((loaded) => this.loaded.set(loaded));
  }
}

/** One entry per adventure (a master's list wins over a player's), sorted by name. */
function merge(player: AdventureDto[], master: AdventureDto[]): Mine[] {
  const byId = new Map<string, Mine>();
  for (const adventure of player) {
    byId.set(adventure.id!, { adventure, role: 'Joueur' });
  }
  for (const adventure of master) {
    byId.set(adventure.id!, { adventure, role: 'MJ' });
  }
  return [...byId.values()].sort((a, b) =>
    (a.adventure.name ?? '').localeCompare(b.adventure.name ?? '', 'fr'),
  );
}
