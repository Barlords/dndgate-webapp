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
import { Router, RouterLink } from '@angular/router';
import { NgTemplateOutlet } from '@angular/common';
import { Subscription } from 'rxjs';
import { AccountDirectory } from '../../../../core/accounts/account-directory.service';
import { ApiConfiguration } from '../../../../core/api/generated/api-configuration';
import {
  getGameCharacters,
  setActiveCharacter,
  setGameCharacterVisibility,
} from '../../../../core/api/generated/functions';
import { AuthService } from '../../../../core/auth/auth.service';
import { NotifierService } from '../../../../core/errors/notifier.service';
import { GameCharacterDto } from '../../../../core/api/generated/models/game-character-dto';
import { Button } from '../../../../shared/ui/button/button';
import { ApiProblem, toApiProblem } from '../../../../core/errors/api-problem';
import { LoadError } from '../../../../shared/ui/load-error/load-error';
import { Loading } from '../../../../shared/ui/loading/loading';
import { AdventureContext } from '../adventure-context.service';
import { CharacterPortrait } from './character-portrait';
import { I18n } from '../../../../core/i18n/i18n.service';
import { AccountBadges } from '../../../../shared/ui/account-badges/account-badges';

type ListState = 'loading' | 'ready' | 'failed';

/** « Personnages »: the portraits of the whole party, for the participants of the adventure. */
@Component({
  selector: 'dg-character-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    LoadError,
    RouterLink,
    Button,
    Loading,
    CharacterPortrait,
    AccountBadges,
    NgTemplateOutlet,
  ],
  styleUrl: '../../tavern-page.scss',
  styles: `
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: var(--dg-space-3);
      margin-bottom: var(--dg-space-4);

      h1 {
        margin: 0;
      }
    }

    .portraits {
      grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
    }

    .card {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dg-space-3);
      height: 100%;
      padding: var(--dg-space-4);
      text-align: center;
      color: var(--dg-ink);
      text-decoration: none;
      background: var(--dg-vellum);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      box-shadow:
        inset 0 0 0 3px var(--dg-vellum),
        inset 0 0 0 4px #c9a24a66;
      transition:
        transform var(--dg-transition),
        box-shadow var(--dg-transition);
    }

    .card:hover,
    .card:focus-visible {
      transform: translateY(-2px);
      box-shadow:
        inset 0 0 0 3px var(--dg-vellum),
        inset 0 0 0 4px var(--dg-gold),
        0 4px 10px #17100a40;
    }

    .name {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-lg);
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--dg-blood);
    }

    .kind,
    .owner {
      margin: 0;
    }

    .owner {
      font-size: var(--dg-text-sm);
      font-style: italic;
      color: var(--dg-ink-muted);
    }

    .active {
      margin: 0;
      font-weight: 700;
      color: var(--dg-blood);
    }

    .npc {
      margin: 0;
      font-size: var(--dg-text-sm);
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--dg-ink-muted);
    }

    .usage {
      margin: 0 0 var(--dg-space-3);
      color: var(--dg-ink-muted);
    }

    section + section {
      margin-top: var(--dg-space-5);
    }

    .switch {
      display: block;
      margin-top: var(--dg-space-2);
      text-align: center;
    }

    @media (prefers-reduced-motion: reduce) {
      .card {
        transition: none;
      }

      .card:hover,
      .card:focus-visible {
        transform: none;
      }
    }
  `,
  template: `
    <div class="head">
      <h1>{{ t().tavern.characters.title }}</h1>
      <dg-button (click)="create()">{{
        context.isMaster() ? t().tavern.characters.createNpc : t().tavern.characters.create
      }}</dg-button>
    </div>
    @switch (state()) {
      @case ('ready') {
        @if (characters().length) {
          @if (showNpcSection()) {
            <section>
              <h2>{{ t().tavern.characters.playersSection }}</h2>
              <ng-container
                [ngTemplateOutlet]="list"
                [ngTemplateOutletContext]="{ $implicit: players() }"
              />
            </section>
            <section>
              <h2>{{ t().tavern.characters.npcSection }}</h2>
              @if (context.isMaster() && npcUsage(); as usage) {
                <p class="usage">{{ t().tavern.characters.npcUsage(usage.used, usage.max) }}</p>
              }
              <ng-container
                [ngTemplateOutlet]="list"
                [ngTemplateOutletContext]="{ $implicit: npcs() }"
              />
            </section>
          } @else {
            <ng-container
              [ngTemplateOutlet]="list"
              [ngTemplateOutletContext]="{ $implicit: players() }"
            />
          }
        } @else {
          <div class="state">
            <p>{{ t().tavern.characters.empty }}</p>
          </div>
        }
      }
      @case ('failed') {
        <div class="state">
          <dg-load-error
            [message]="t().tavern.characters.loadError"
            [problem]="loadProblem()"
            (retry)="load()"
          />
        </div>
      }
      @default {
        <div class="state">
          <dg-loading
            [label]="t().tavern.characters.loading"
            [message]="t().tavern.characters.loadingFlavour"
          />
        </div>
      }
    }
    <ng-template #list let-items>
      <ul class="cards portraits">
        @for (character of items; track character.id) {
          <li>
            <a
              class="card"
              [routerLink]="['/tavern/adventures', adventureId(), 'characters', character.id]"
            >
              <dg-character-portrait
                [characterId]="character.id!"
                [name]="character.name ?? ''"
                [hasPicture]="!!character.pictureUrl"
              />
              <span class="name">{{ character.name }}</span>
              <p class="kind">{{ character.race }} · {{ character.martialClass }}</p>
              <p class="owner">
                {{ pseudo(character.ownerId!)
                }}<dg-account-badges [accountId]="character.ownerId" />
              </p>
              @if (character.npc) {
                <p class="npc">{{ t().tavern.characters.npcTag }}</p>
              }
              @if (isActive(character)) {
                <p class="active">{{ t().tavern.characters.active }}</p>
              }
            </a>
            @if (character.npc && context.isMaster()) {
              <dg-button
                class="switch"
                variant="ghost"
                [pressed]="character.visibility === 'PUBLIC'"
                (click)="toggleVisibility(character)"
              >
                {{
                  character.visibility === 'PUBLIC'
                    ? t().tavern.characters.npcPublic
                    : t().tavern.characters.npcPrivate
                }}
                <span class="dg-visually-hidden">{{ character.name }}</span>
              </dg-button>
            }
            @if (canActivate(character)) {
              <dg-button
                class="switch"
                variant="ghost"
                [loading]="switching() === character.id"
                (click)="activate(character)"
              >
                {{ t().tavern.characters.makeActive }}
              </dg-button>
            }
          </li>
        }
      </ul>
    </ng-template>
  `,
})
export class CharacterListPage {
  protected readonly t = inject(I18n).t;

  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  protected readonly context = inject(AdventureContext);
  private readonly directory = inject(AccountDirectory);
  private readonly auth = inject(AuthService);
  private readonly notifier = inject(NotifierService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<ListState>('loading');
  protected readonly loadProblem = signal<ApiProblem | null>(null);
  protected readonly characters = signal<GameCharacterDto[]>([]);
  protected readonly players = computed(() => this.characters().filter((c) => !c.npc));
  protected readonly npcs = computed(() => this.characters().filter((c) => c.npc));
  /** The master always has the NPC section (to see the quota); players only when there is one. */
  protected readonly showNpcSection = computed(
    () => this.context.isMaster() || this.npcs().length > 0,
  );
  protected readonly npcUsage = computed(() => this.context.usage('npcsPerAdventure'));
  /** The id of the character being made active, if any. */
  protected readonly switching = signal<string | null>(null);
  private request: Subscription | null = null;
  private requested = false;

  protected adventureId(): string {
    return this.context.adventure()!.id!;
  }

  constructor() {
    effect(() => {
      // No request before the reader is known to be a participant: a visitor is redirected away
      // by the shell, and the API would refuse them.
      if (this.context.state() === 'ready' && this.context.isParticipant()) {
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
    this.request?.unsubscribe();
    this.request = getGameCharacters(this.http, this.rootUrl, {
      adventureId: this.adventureId(),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.characters.set(body);
          this.state.set('ready');
        },
        error: (error: unknown) => {
          this.loadProblem.set(toApiProblem(error));
          this.state.set('failed');
        },
      });
  }

  /** The character a player plays now; non-player characters have no such notion. */
  protected isActive(character: GameCharacterDto): boolean {
    return !character.npc && character.active === true;
  }

  /** Only the owner switches, only among their player characters, and only to another one. */
  protected canActivate(character: GameCharacterDto): boolean {
    return (
      !character.npc &&
      !character.active &&
      character.ownerId === this.auth.account()?.id &&
      this.context.isPlayer()
    );
  }

  protected activate(character: GameCharacterDto): void {
    this.switching.set(character.id!);
    setActiveCharacter(this.http, this.rootUrl, {
      id: this.adventureId(),
      body: { characterId: character.id! },
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.switching.set(null);
          // One active character per player: the others of that player lose the mark.
          this.characters.update((all) =>
            all.map((c) =>
              c.npc || c.ownerId !== body.ownerId ? c : { ...c, active: c.id === body.id },
            ),
          );
          this.context.reloadEntitlements();
          this.notifier.success(this.t().tavern.characters.activated(body.name ?? ''));
        },
        error: (error: unknown) => {
          this.switching.set(null);
          this.notifier.error(toApiProblem(error), this.t().tavern.characters.activating);
        },
      });
  }

  /** One call; the toggle moves at once and goes back if the API refuses. */
  protected toggleVisibility(character: GameCharacterDto): void {
    const previous = character.visibility;
    const next = previous === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';
    const show = (visibility: GameCharacterDto['visibility']) =>
      this.characters.update((all) =>
        all.map((c) => (c.id === character.id ? { ...c, visibility } : c)),
      );
    show(next);
    setGameCharacterVisibility(this.http, this.rootUrl, {
      id: character.id!,
      body: { visibility: next },
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => show(body.visibility),
        error: (error: unknown) => {
          show(previous);
          this.notifier.error(toApiProblem(error), this.t().tavern.characters.npcVisibility);
        },
      });
  }

  protected create(): void {
    void this.router.navigate(['/tavern/adventures', this.adventureId(), 'characters', 'new']);
  }
}
