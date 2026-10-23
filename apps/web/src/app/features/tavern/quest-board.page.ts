import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { getAvailableAdventures, joinAdventure } from '../../core/api/generated/functions';
import { PageDtoAdventureDto } from '../../core/api/generated/models/page-dto-adventure-dto';
import { ApiProblem, toApiProblem } from '../../core/errors/api-problem';
import { NotifierService } from '../../core/errors/notifier.service';
import { Button } from '../../shared/ui/button/button';
import { LoadError } from '../../shared/ui/load-error/load-error';
import { Paginator } from '../../shared/ui/paginator/paginator';
import { Parchment } from '../../shared/ui/parchment/parchment';
import { Loading } from '../../shared/ui/loading/loading';
import { ViewMode } from '../../shared/ui/view-toggle/view-toggle';
import { AdventureCard } from './adventure-card';
import { STATUS_LABEL, Status, VISIBILITY_LABEL, Visibility } from './adventure-status';
import { ListHeader } from './list-header';

export const QUESTS_PAGE_SIZE = 20;

type Board =
  | { state: 'loading' }
  | { state: 'failed'; problem: ApiProblem }
  | { state: 'ready'; page: PageDtoAdventureDto };

/**
 * « Aventures »: the public adventures still looking for players, paged by the server, as a table or
 * as cards.
 */
@Component({
  selector: 'dg-quest-board-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    LoadError,
    Parchment,
    AdventureCard,
    Button,
    Paginator,
    Loading,
    ListHeader,
    RouterLink,
  ],
  styleUrl: './tavern-page.scss',
  template: `
    <dg-parchment>
      <dg-list-header
        heading="Aventures"
        storageKey="dg.tavern.adventures.view"
        createLabel="Créer mon aventure"
        [(mode)]="view"
        (create)="createAdventure()"
      />
      @let current = board();
      @if (current.state === 'ready') {
        @if (current.page.content?.length) {
          @if (view() === 'table') {
            <div class="table-scroll">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">Nom</th>
                    <th scope="col">Statut</th>
                    <th scope="col">Lieu</th>
                    <th scope="col">Joueurs</th>
                    <th scope="col">Visibilité</th>
                    <th scope="col" class="table__actions">
                      <span class="dg-visually-hidden">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  @for (quest of current.page.content; track quest.id) {
                    <tr>
                      <td>
                        <a [routerLink]="['/tavern/adventures', quest.id]">{{ quest.name }}</a>
                        @if (quest.masterVacant) {
                          <span class="muted"> · MJ recherché</span>
                        }
                      </td>
                      <td>{{ statusLabel(quest.status) }}</td>
                      <td>{{ quest.city }} ({{ quest.postalCode }})</td>
                      <td>{{ quest.players?.length ?? 0 }} / {{ quest.maxPlayer }}</td>
                      <td>{{ visibilityLabel(quest.visibility) }}</td>
                      <td class="table__actions">
                        <dg-button [loading]="joining() === quest.id" (click)="join(quest.id!)">
                          Rejoindre
                        </dg-button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <ul class="cards">
              @for (quest of current.page.content; track quest.id) {
                <li>
                  <dg-adventure-card [adventure]="quest">
                    <dg-button [loading]="joining() === quest.id" (click)="join(quest.id!)">
                      Rejoindre
                    </dg-button>
                  </dg-adventure-card>
                </li>
              }
            </ul>
          }
          <dg-paginator
            [page]="page()"
            [totalPages]="current.page.totalPages ?? 1"
            (pageChange)="page.set($event)"
          />
        } @else {
          <p>Aucune aventure ne t’attend pour le moment. Crée la tienne !</p>
        }
      } @else if (current.state === 'failed') {
        <div class="state">
          <dg-load-error
            message="Impossible de charger les aventures."
            [problem]="current.problem"
            (retry)="reload()"
          />
        </div>
      } @else {
        <div class="state">
          <dg-loading
            label="Chargement des aventures"
            message="Le tavernier épingle les avis de quête…"
          />
        </div>
      }
    </dg-parchment>
  `,
})
export class QuestBoardPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly router = inject(Router);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly view = signal<ViewMode>('cards');
  protected readonly page = signal(0);
  /** Bumped to load the same page again. */
  private readonly refresh = signal(0);
  protected readonly joining = signal<string | null>(null);
  protected readonly board = signal<Board>({ state: 'loading' });

  private readonly request = computed(() => ({ page: this.page(), refresh: this.refresh() }));

  constructor() {
    toObservable(this.request)
      .pipe(
        switchMap(({ page }) =>
          getAvailableAdventures(this.http, this.rootUrl, { page, size: QUESTS_PAGE_SIZE }).pipe(
            map((response): Board => ({ state: 'ready', page: response.body })),
            catchError((error: unknown) =>
              of<Board>({ state: 'failed', problem: toApiProblem(error) }),
            ),
            startWith<Board>({ state: 'loading' }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((board) => this.board.set(board));
  }

  protected statusLabel(status: string | undefined): string {
    return STATUS_LABEL[status as Status] ?? '';
  }

  protected visibilityLabel(visibility: string | undefined): string {
    return VISIBILITY_LABEL[visibility as Visibility] ?? '';
  }

  protected createAdventure(): void {
    void this.router.navigate(['/tavern/new']);
  }

  protected reload(): void {
    this.refresh.update((n) => n + 1);
  }

  protected join(id: string): void {
    this.joining.set(id);
    joinAdventure(this.http, this.rootUrl, { id })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.joining.set(null);
          void this.router.navigate(['/tavern/adventures', id]);
        },
        error: (error: unknown) => {
          this.joining.set(null);
          this.notifier.error(toApiProblem(error), 'Rejoindre l’aventure');
          // It may have started or filled up meanwhile: show the board as it is now.
          this.reload();
        },
      });
  }
}
