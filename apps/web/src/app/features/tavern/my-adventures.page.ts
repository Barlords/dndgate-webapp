import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, Subject, switchMap } from 'rxjs';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { getAdventuresAsMaster, getAdventuresAsPlayer } from '../../core/api/generated/functions';
import { AdventureDto } from '../../core/api/generated/models/adventure-dto';
import { fetchAllPages, PAGE_SIZE } from '../../core/api/fetch-all-pages';
import { ApiProblem, toApiProblem } from '../../core/errors/api-problem';
import { LoadError } from '../../shared/ui/load-error/load-error';
import { Parchment } from '../../shared/ui/parchment/parchment';
import { Loading } from '../../shared/ui/loading/loading';
import { ViewMode } from '../../shared/ui/view-toggle/view-toggle';
import { AdventureCard } from './adventure-card';
import { ListHeader } from './list-header';
import { I18n } from '../../core/i18n/i18n.service';

interface Mine {
  adventure: AdventureDto;
  role: 'master' | 'player';
}

type Loaded =
  { state: 'loading' } | { state: 'failed'; problem: ApiProblem } | { state: 'ready'; all: Mine[] };

/** « Mes aventures »: those the reader plays or runs, whatever their status, as a table or cards. */
@Component({
  selector: 'dg-my-adventures-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Parchment, AdventureCard, Loading, ListHeader, RouterLink],
  styleUrl: './tavern-page.scss',
  template: `
    <dg-parchment>
      <dg-list-header
        [heading]="t().tavern.mine.title"
        storageKey="dg.tavern.mine.view"
        [createLabel]="t().tavern.board.create"
        [(mode)]="view"
        (create)="createAdventure()"
      />
      @let current = loaded();
      @if (current.state === 'ready') {
        @if (current.all.length > 0) {
          @if (view() === 'table') {
            <div class="table-scroll">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">{{ t().tavern.columns.name }}</th>
                    <th scope="col">{{ t().tavern.columns.role }}</th>
                    <th scope="col">{{ t().tavern.columns.status }}</th>
                    <th scope="col">{{ t().tavern.columns.place }}</th>
                    <th scope="col">{{ t().tavern.columns.players }}</th>
                    <th scope="col">{{ t().tavern.columns.visibility }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of current.all; track item.adventure.id) {
                    <tr>
                      <td>
                        <a [routerLink]="['/tavern/adventures', item.adventure.id]">{{
                          item.adventure.name
                        }}</a>
                      </td>
                      <td>{{ t().tavern.roles[item.role] }}</td>
                      <td>{{ statusLabel(item.adventure.status) }}</td>
                      <td>{{ item.adventure.city }} ({{ item.adventure.postalCode }})</td>
                      <td>
                        {{ item.adventure.players?.length ?? 0 }} / {{ item.adventure.maxPlayer }}
                      </td>
                      <td>{{ visibilityLabel(item.adventure.visibility) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <ul class="cards">
              @for (item of current.all; track item.adventure.id) {
                <li>
                  <dg-adventure-card
                    [adventure]="item.adventure"
                    [role]="t().tavern.roles[item.role]"
                  />
                </li>
              }
            </ul>
          }
        } @else {
          <p>{{ t().tavern.mine.empty }}</p>
        }
      } @else if (current.state === 'failed') {
        <div class="state">
          <dg-load-error
            [message]="t().tavern.mine.loadError"
            [problem]="current.problem"
            (retry)="retries.next()"
          />
        </div>
      } @else {
        <div class="state">
          <dg-loading
            [label]="t().tavern.board.loading"
            [message]="t().tavern.mine.loadingFlavour"
          />
        </div>
      }
    </dg-parchment>
  `,
})
export class MyAdventuresPage {
  protected readonly t = inject(I18n).t;

  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly router = inject(Router);

  protected readonly view = signal<ViewMode>('cards');
  protected readonly retries = new Subject<void>();
  protected readonly loaded = signal<Loaded>({ state: 'loading' });

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
            catchError((error: unknown) =>
              of<Loaded>({ state: 'failed', problem: toApiProblem(error) }),
            ),
            startWith<Loaded>({ state: 'loading' }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((loaded) => this.loaded.set(loaded));
  }

  protected statusLabel(status: string | undefined): string {
    return this.t().tavern.status[status ?? ''] ?? '';
  }

  protected visibilityLabel(visibility: string | undefined): string {
    return this.t().tavern.visibility[visibility ?? ''] ?? '';
  }

  protected createAdventure(): void {
    void this.router.navigate(['/tavern/new']);
  }
}

/** One entry per adventure (a master's list wins over a player's), sorted by name. */
function merge(player: AdventureDto[], master: AdventureDto[]): Mine[] {
  const byId = new Map<string, Mine>();
  for (const adventure of player) {
    byId.set(adventure.id!, { adventure, role: 'player' });
  }
  for (const adventure of master) {
    byId.set(adventure.id!, { adventure, role: 'master' });
  }
  return [...byId.values()].sort((a, b) =>
    (a.adventure.name ?? '').localeCompare(b.adventure.name ?? '', 'fr'),
  );
}
