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
import { filter } from 'rxjs';
import { AccountDirectory } from '../../../../core/accounts/account-directory.service';
import { ApiConfiguration } from '../../../../core/api/generated/api-configuration';
import {
  deleteGameCharacterById,
  getGameCharacterById,
} from '../../../../core/api/generated/functions';
import { GameCharacterDto } from '../../../../core/api/generated/models/game-character-dto';
import { AuthService } from '../../../../core/auth/auth.service';
import { toApiProblem } from '../../../../core/errors/api-problem';
import { NotifierService } from '../../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../../layout/breadcrumb.service';
import { Button } from '../../../../shared/ui/button/button';
import { ConfirmService } from '../../../../shared/ui/confirm-dialog/confirm.service';
import { Spinner } from '../../../../shared/ui/spinner/spinner';
import { AdventureContext } from '../adventure-context.service';
import { CharacterPortrait } from './character-portrait';

type DetailState = 'loading' | 'ready' | 'missing' | 'failed';

/** One character: portrait and fields; its owner can edit or delete it. */
@Component({
  selector: 'dg-character-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Button, Spinner, CharacterPortrait],
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
          <dg-character-portrait [characterId]="c.id!" [name]="c.name ?? ''" />
          <div class="identity">
            <h1>{{ c.name }}</h1>
            <p class="owner">{{ pseudo(c.ownerId!) }}</p>
            <dl>
              <dt>Race</dt>
              <dd>{{ c.race }}</dd>
              <dt>Classe</dt>
              <dd>{{ c.martialClass }}</dd>
            </dl>
          </div>
        </div>
        <h2>Personnalité</h2>
        <p class="text">{{ c.personality }}</p>
        <h2>Histoire</h2>
        <p class="text">{{ c.background }}</p>
        @if (isOwner()) {
          <div class="actions">
            <dg-button (click)="edit()">Modifier</dg-button>
            <dg-button variant="danger" [loading]="deleting()" (click)="remove()">
              Supprimer
            </dg-button>
          </div>
        }
        <p><a [routerLink]="listLink()">Retour aux personnages</a></p>
      }
      @case ('missing') {
        <div class="state">
          <p role="alert">Ce personnage est introuvable.</p>
          <a [routerLink]="listLink()">Retour aux personnages</a>
        </div>
      }
      @case ('failed') {
        <div class="state">
          <p role="alert">Impossible de charger le personnage.</p>
          <dg-button variant="ghost" (click)="load()">Réessayer</dg-button>
        </div>
      }
      @default {
        <div class="state"><dg-spinner label="Chargement du personnage" /></div>
      }
    }
  `,
})
export class CharacterDetailPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly context = inject(AdventureContext);
  private readonly auth = inject(AuthService);
  private readonly directory = inject(AccountDirectory);
  private readonly confirmer = inject(ConfirmService);
  private readonly notifier = inject(NotifierService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly characterId = inject(ActivatedRoute).snapshot.paramMap.get('characterId')!;

  protected readonly state = signal<DetailState>('loading');
  protected readonly character = signal<GameCharacterDto | null>(null);
  protected readonly deleting = signal(false);
  private requested = false;

  protected readonly isOwner = computed(
    () => this.character()?.ownerId === this.auth.account()?.id,
  );
  protected readonly listLink = computed(() => [
    '/tavern/adventures',
    this.context.adventure()?.id,
    'characters',
  ]);

  constructor() {
    effect(() => {
      // No request before the reader is known to be a participant: a visitor is redirected away.
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
    getGameCharacterById(this.http, this.rootUrl, { id: this.characterId })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.character.set(body);
          this.state.set('ready');
          this.breadcrumb.setLast(body.name ?? '');
        },
        error: (error: unknown) =>
          this.state.set(toApiProblem(error).status === 404 ? 'missing' : 'failed'),
      });
  }

  protected edit(): void {
    void this.router.navigate([...this.listLink(), this.characterId, 'edit']);
  }

  protected remove(): void {
    const character = this.character()!;
    this.confirmer
      .typeToConfirm({
        title: 'Supprimer le personnage',
        message: `Cette suppression est irréversible. Retape le nom du personnage (« ${character.name} ») pour confirmer.`,
        confirmLabel: 'Supprimer',
        inputLabel: 'Nom du personnage',
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
              this.notifier.success('Personnage supprimé.');
              void this.router.navigate(this.listLink());
            },
            error: (error: unknown) => {
              this.deleting.set(false);
              this.notifier.error(toApiProblem(error), 'Suppression du personnage');
            },
          });
      });
  }
}
