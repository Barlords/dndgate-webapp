import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';
import { AccountDirectory } from '../../../../core/accounts/account-directory.service';
import { fetchAllPages, PAGE_SIZE } from '../../../../core/api/fetch-all-pages';
import { ApiConfiguration } from '../../../../core/api/generated/api-configuration';
import {
  deleteGameCharacterById,
  getAdventuresAsPlayer,
  getGameCharacterById,
  moveGameCharacterToAdventure,
  moveGameCharacterToReserve,
} from '../../../../core/api/generated/functions';
import { AdventureDto } from '../../../../core/api/generated/models/adventure-dto';
import { GameCharacterDto } from '../../../../core/api/generated/models/game-character-dto';
import { AuthService } from '../../../../core/auth/auth.service';
import { ApiProblem, toApiProblem } from '../../../../core/errors/api-problem';
import { NotifierService } from '../../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../../layout/breadcrumb.service';
import { Button } from '../../../../shared/ui/button/button';
import { LoadError } from '../../../../shared/ui/load-error/load-error';
import { ConfirmService } from '../../../../shared/ui/confirm-dialog/confirm.service';
import { Loading } from '../../../../shared/ui/loading/loading';
import { ReserveFullDialog } from '../../characters/reserve-full.dialog';
import { AdventureContext } from '../adventure-context.service';
import { CharacterPortrait } from './character-portrait';
import { I18n } from '../../../../core/i18n/i18n.service';
import { AccountBadges } from '../../../../shared/ui/account-badges/account-badges';

type DetailState = 'loading' | 'ready' | 'missing' | 'failed';

/** The key of the plan limit hit when the reserve (characters outside any adventure) is full. */
const RESERVE_LIMIT = 'charactersWithoutAdventure';

/** The adventures a character can still be sent to. */
const OPEN_STATUSES: ReadonlySet<AdventureDto['status']> = new Set(['NOT_STARTED', 'ONGOING']);

/**
 * One character: portrait and fields; its owner can edit or delete it. Under « Mes personnages »
 * (no adventure around), it also leads to its adventure, if it has one.
 */
@Component({
  selector: 'dg-character-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, RouterLink, Button, Loading, CharacterPortrait, AccountBadges],
  styleUrl: '../../tavern-page.scss',
  styles: `
    .sheet {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      gap: var(--dg-space-5);
      margin-bottom: var(--dg-space-4);
      --dg-portrait-width: 12rem;
    }

    .identity {
      flex: 1 1 14rem;
    }

    .identity h1 {
      margin-bottom: var(--dg-space-2);
    }

    .adventure {
      margin: 0 0 var(--dg-space-3);
    }

    .owner {
      margin: 0 0 var(--dg-space-3);
      font-style: italic;
      color: var(--dg-ink-muted);
    }

    h2 {
      margin: var(--dg-space-4) 0 var(--dg-space-2);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-md);
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--dg-title-color);
    }

    .text {
      margin: 0;
      white-space: pre-line;
    }

    .picker {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: var(--dg-space-3);
      margin-top: var(--dg-space-4);
    }

    .picker label {
      display: flex;
      flex-direction: column;
      gap: var(--dg-space-1);
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-2);
      margin-top: var(--dg-space-5);
    }
  `,
  template: `
    @switch (state()) {
      @case ('ready') {
        @let c = character()!;
        <div class="sheet">
          <dg-character-portrait
            [characterId]="c.id!"
            [name]="c.name ?? ''"
            [hasPicture]="!!c.pictureUrl"
          />
          <div class="identity">
            <h1>{{ c.name }}</h1>
            <p class="owner">
              {{ pseudo(c.ownerId!) }}<dg-account-badges [accountId]="c.ownerId" />
            </p>
            @if (!inAdventure) {
              <p class="adventure">
                @if (c.adventureId) {
                  <a [routerLink]="['/tavern/adventures', c.adventureId]">{{
                    t().tavern.characters.seeAdventure
                  }}</a>
                } @else {
                  {{ t().tavern.characters.noAdventure }}
                }
              </p>
            }
            <dl>
              <dt>{{ t().tavern.characters.race }}</dt>
              <dd>{{ c.race }}</dd>
              <dt>{{ t().tavern.characters.class }}</dt>
              <dd>{{ c.martialClass }}</dd>
            </dl>
          </div>
        </div>
        <h2>{{ t().tavern.characters.personality }}</h2>
        <p class="text">{{ c.personality }}</p>
        <h2>{{ t().tavern.characters.background }}</h2>
        <p class="text">{{ c.background }}</p>
        @if (canManage()) {
          <div class="actions">
            <dg-button (click)="edit()">{{ t().common.actions.edit }}</dg-button>
            @if (!c.npc && c.adventureId) {
              <dg-button variant="ghost" [loading]="reserving()" (click)="reserve()">
                {{ t().tavern.characters.toReserve }}
              </dg-button>
            }
            @if (!c.npc && !c.adventureId && !inAdventure) {
              <dg-button variant="ghost" (click)="choose()">
                {{ t().tavern.characters.sendToAdventure }}
              </dg-button>
            }
            <dg-button variant="danger" [loading]="deleting()" (click)="remove()">
              {{ t().common.actions.delete }}
            </dg-button>
          </div>
          @if (picker(); as choice) {
            @if (choice === 'loading') {
              <dg-loading [label]="t().tavern.characters.loadingAdventures" />
            } @else if (choice.length === 0) {
              <p role="status">{{ t().tavern.characters.noPlayableAdventure }}</p>
            } @else {
              <div class="picker">
                <label>
                  {{ t().tavern.characters.sendTo }}
                  <select (change)="pick($event)">
                    @for (option of choice; track option.id) {
                      <option [value]="option.id" [selected]="option.id === destination()">
                        {{ option.name }}
                      </option>
                    }
                  </select>
                </label>
                <dg-button [loading]="sending()" (click)="send()">
                  {{ t().tavern.characters.send }}
                </dg-button>
              </div>
            }
          }
        }
        <p>
          <a [routerLink]="listLink()">{{ t().tavern.characters.back }}</a>
        </p>
      }
      @case ('missing') {
        <div class="state">
          <p role="alert">{{ t().tavern.characters.notFound }}</p>
          <a [routerLink]="listLink()">{{ t().tavern.characters.back }}</a>
        </div>
      }
      @case ('failed') {
        <div class="state">
          <dg-load-error
            [message]="t().tavern.characters.oneLoadError"
            [problem]="loadProblem()"
            (retry)="load()"
          />
        </div>
      }
      @default {
        <div class="state">
          <dg-loading
            [label]="t().tavern.characters.oneLoading"
            [message]="t().tavern.characters.oneLoadingFlavour"
          />
        </div>
      }
    }
  `,
})
export class CharacterDetailPage {
  protected readonly t = inject(I18n).t;

  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  /** Null under « Mes personnages ». */
  private readonly context = inject(AdventureContext, { optional: true });
  private readonly auth = inject(AuthService);
  private readonly directory = inject(AccountDirectory);
  private readonly confirmer = inject(ConfirmService);
  private readonly reserveFull = inject(ReserveFullDialog);
  private readonly notifier = inject(NotifierService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly characterId = inject(ActivatedRoute).snapshot.paramMap.get('characterId')!;

  protected readonly state = signal<DetailState>('loading');
  protected readonly loadProblem = signal<ApiProblem | null>(null);
  protected readonly character = signal<GameCharacterDto | null>(null);
  protected readonly deleting = signal(false);
  protected readonly reserving = signal(false);
  protected readonly sending = signal(false);
  /** The adventures to choose from once asked (`loading` meanwhile); null while not asked. */
  protected readonly picker = signal<AdventureDto[] | 'loading' | null>(null);
  protected readonly destination = signal('');
  private requested = false;

  protected readonly isOwner = computed(
    () => this.character()?.ownerId === this.auth.account()?.id,
  );
  /** An NPC belongs to the current master whoever owns it; any other character to its owner. */
  protected readonly canManage = computed(() =>
    this.character()?.npc ? (this.context?.isMaster() ?? false) : this.isOwner(),
  );
  protected readonly inAdventure = this.context !== null;
  protected readonly listLink = computed(() =>
    this.context === null
      ? ['/tavern/characters']
      : ['/tavern/adventures', this.context.adventure()?.id, 'characters'],
  );

  constructor() {
    const context = this.context;
    if (context === null) {
      this.load();
      return;
    }
    effect(() => {
      // No request before the reader is known to be a participant: a visitor is redirected away.
      if (context.state() === 'ready' && context.isParticipant()) {
        untracked(() => {
          if (!this.requested) {
            this.load();
          }
        });
      }
    });
  }

  protected pseudo(id: string): string {
    return this.directory.pseudo(id)();
  }

  protected load(): void {
    this.requested = true;
    this.state.set('loading');
    getGameCharacterById(this.http, this.rootUrl, { id: this.characterId })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.character.set(body);
          this.state.set('ready');
          this.breadcrumb.setLast(body.name ?? '');
        },
        error: (error: unknown) => {
          const problem = toApiProblem(error);
          this.loadProblem.set(problem);
          this.state.set(problem.status === 404 ? 'missing' : 'failed');
        },
      });
  }

  protected edit(): void {
    void this.router.navigate([...this.listLink(), this.characterId, 'edit']);
  }

  protected reserve(): void {
    this.reserving.set(true);
    moveGameCharacterToReserve(this.http, this.rootUrl, { id: this.characterId })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.reserving.set(false);
          this.notifier.success(this.t().tavern.characters.reserved);
          this.context?.reloadEntitlements();
          if (this.inAdventure) {
            void this.router.navigate(this.listLink());
          } else {
            this.character.set(body);
          }
        },
        error: (error: unknown) => {
          this.reserving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409 && problem.plan?.key === RESERVE_LIMIT) {
            this.offerDeletion();
          } else {
            this.notifier.error(problem, this.t().tavern.characters.reserving);
          }
        },
      });
  }

  /** The reserve is full: the reader may give the character up, but must retype its name. */
  private offerDeletion(): void {
    this.reserveFull
      .ask(this.character()?.name ?? '')
      .pipe(
        filter((answer) => answer === 'delete'),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.remove());
  }

  /** Lists the adventures the reader plays in that a character can be sent to. */
  protected choose(): void {
    this.picker.set('loading');
    fetchAllPages((page) =>
      getAdventuresAsPlayer(this.http, this.rootUrl, { page, size: PAGE_SIZE }).pipe(
        map((response) => response.body),
      ),
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (adventures) => {
          const open = adventures.filter((a) => OPEN_STATUSES.has(a.status));
          this.destination.set(open[0]?.id ?? '');
          this.picker.set(open);
        },
        error: (error: unknown) => {
          this.picker.set(null);
          this.notifier.error(toApiProblem(error), this.t().tavern.characters.loadingAdventures);
        },
      });
  }

  protected pick(event: Event): void {
    this.destination.set((event.target as HTMLSelectElement).value);
  }

  protected send(): void {
    this.sending.set(true);
    moveGameCharacterToAdventure(this.http, this.rootUrl, {
      id: this.characterId,
      body: { adventureId: this.destination() },
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.sending.set(false);
          this.picker.set(null);
          this.character.set(body);
          this.notifier.success(this.t().tavern.characters.sent);
        },
        error: (error: unknown) => {
          this.sending.set(false);
          this.notifier.error(toApiProblem(error), this.t().tavern.characters.sending);
        },
      });
  }

  protected remove(): void {
    const character = this.character()!;
    this.confirmer
      .typeToConfirm({
        title: this.t().tavern.characters.deleteTitle,
        message: this.t().tavern.characters.deleteMessage(character.name ?? ''),
        confirmLabel: this.t().common.actions.delete,
        inputLabel: this.t().tavern.characters.deleteInput,
        inputType: 'text',
        expected: character.name,
      })
      .pipe(
        filter((typed): typed is string => typed !== null),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((confirmation) => {
        this.deleting.set(true);
        deleteGameCharacterById(this.http, this.rootUrl, {
          id: this.characterId,
          body: { confirmation },
        })
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.deleting.set(false);
              this.notifier.success(this.t().tavern.characters.deleted);
              this.context?.reloadEntitlements();
              void this.router.navigate(this.listLink());
            },
            error: (error: unknown) => {
              this.deleting.set(false);
              this.notifier.error(toApiProblem(error), this.t().tavern.characters.deleting);
            },
          });
      });
  }
}
