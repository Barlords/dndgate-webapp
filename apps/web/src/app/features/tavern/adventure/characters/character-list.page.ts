import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AccountDirectory } from '../../../../core/accounts/account-directory.service';
import { ApiConfiguration } from '../../../../core/api/generated/api-configuration';
import { getGameCharacters } from '../../../../core/api/generated/functions';
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
  imports: [LoadError, RouterLink, Button, Loading, CharacterPortrait, AccountBadges],
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
      <dg-button (click)="create()">{{ t().tavern.characters.create }}</dg-button>
    </div>
    @switch (state()) {
      @case ('ready') {
        @if (characters().length) {
          <ul class="cards portraits">
            @for (character of characters(); track character.id) {
              <li>
                <a
                  class="card"
                  [routerLink]="['/tavern/adventures', adventureId(), 'characters', character.id]"
                >
                  <dg-character-portrait
                    [characterId]="character.id!"
                    [name]="character.name ?? ''"
                  />
                  <span class="name">{{ character.name }}</span>
                  <p class="kind">{{ character.race }} · {{ character.martialClass }}</p>
                  <p class="owner">
                    {{ pseudo(character.ownerId!)
                    }}<dg-account-badges [accountId]="character.ownerId" />
                  </p>
                </a>
              </li>
            }
          </ul>
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
  `,
})
export class CharacterListPage {
  protected readonly t = inject(I18n).t;

  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly context = inject(AdventureContext);
  private readonly directory = inject(AccountDirectory);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<ListState>('loading');
  protected readonly loadProblem = signal<ApiProblem | null>(null);
  protected readonly characters = signal<GameCharacterDto[]>([]);
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

  protected create(): void {
    void this.router.navigate(['/tavern/adventures', this.adventureId(), 'characters', 'new']);
  }
}
