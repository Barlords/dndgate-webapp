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
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiConfiguration } from '../../../../core/api/generated/api-configuration';
import {
  createGameCharacter,
  getGameCharacterById,
  patchGameCharacter,
} from '../../../../core/api/generated/functions';
import { GameCharacterDto } from '../../../../core/api/generated/models/game-character-dto';
import { AuthService } from '../../../../core/auth/auth.service';
import { ApiProblem, toApiProblem } from '../../../../core/errors/api-problem';
import { NotifierService } from '../../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../../layout/breadcrumb.service';
import { Button } from '../../../../shared/ui/button/button';
import { Spinner } from '../../../../shared/ui/spinner/spinner';
import { AdventureContext } from '../adventure-context.service';
import { CharacterForm, CharacterSubmission, CharacterValue } from './character-form';

const LIMIT_REACHED = 'Tu as déjà 10 personnages dans cette aventure.';

function toValue(character: GameCharacterDto): CharacterValue {
  return {
    name: character.name ?? '',
    race: character.race ?? '',
    martialClass: character.martialClass ?? '',
    personality: character.personality ?? '',
    background: character.background ?? '',
  };
}

type LoadState = 'loading' | 'ready' | 'missing' | 'failed';

/** Creation of a character (`characters/new`) and edition of one of the reader's (`…/edit`). */
@Component({
  selector: 'dg-character-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CharacterForm, RouterLink, Button, Spinner],
  styleUrl: '../../tavern-page.scss',
  template: `
    <h1>{{ characterId ? 'Modifier le personnage' : 'Nouveau personnage' }}</h1>
    @switch (state()) {
      @case ('ready') {
        @if (!characterId || isOwner()) {
          <dg-character-form
            [initial]="initial()"
            [withPicture]="!characterId"
            [submitLabel]="characterId ? 'Enregistrer' : 'Créer le personnage'"
            [busy]="saving()"
            [problem]="problem()"
            (saved)="save($event)"
          />
        } @else {
          <div class="state">
            <p role="alert">Seul le propriétaire du personnage peut le modifier.</p>
            <a [routerLink]="backLink()">Retour au personnage</a>
          </div>
        }
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
export class CharacterFormPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly context = inject(AdventureContext);
  private readonly auth = inject(AuthService);
  private readonly notifier = inject(NotifierService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly form = viewChild(CharacterForm);

  /** The edited character, from the `:characterId` route above this one; null when creating. */
  protected readonly characterId =
    inject(ActivatedRoute).parent?.snapshot.paramMap.get('characterId') ?? null;

  private readonly loaded = signal<GameCharacterDto | null>(null);
  protected readonly state = signal<LoadState>(this.characterId === null ? 'ready' : 'loading');
  protected readonly initial = signal<CharacterValue>({
    name: '',
    race: '',
    martialClass: '',
    personality: '',
    background: '',
  });
  protected readonly saving = signal(false);
  protected readonly problem = signal<string | null>(null);
  private request: Subscription | null = null;

  protected readonly isOwner = computed(() => this.loaded()?.ownerId === this.auth.account()?.id);

  protected readonly listLink = computed(() => [
    '/tavern/adventures',
    this.context.adventure()?.id,
    'characters',
  ]);
  protected readonly backLink = computed(() => [...this.listLink(), this.characterId]);

  constructor() {
    effect(() => {
      // No request before the reader is known to be a participant: a visitor is redirected away.
      if (this.context.state() === 'ready' && this.context.isParticipant()) {
        untracked(() => {
          if (this.characterId !== null && this.loaded() === null) {
            this.load();
          }
        });
      }
    });
  }

  protected load(): void {
    if (this.characterId === null) {
      return;
    }
    this.state.set('loading');
    this.fetch(this.characterId, () => undefined);
  }

  private fetch(id: string, done: (character: GameCharacterDto) => void): void {
    this.request?.unsubscribe();
    this.request = getGameCharacterById(this.http, this.rootUrl, { id })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.loaded.set(body);
          this.initial.set(toValue(body));
          this.state.set('ready');
          this.breadcrumb.setLabelAt(
            `/tavern/adventures/${this.context.adventure()!.id}/characters/${id}`,
            body.name ?? '',
          );
          done(body);
        },
        error: (error: unknown) => {
          // A failed reload keeps what is shown.
          if (this.loaded() === null) {
            this.state.set(toApiProblem(error).status === 404 ? 'missing' : 'failed');
          } else {
            this.notifier.error(toApiProblem(error), 'Chargement du personnage');
          }
        },
      });
  }

  protected save(submission: CharacterSubmission): void {
    this.problem.set(null);
    this.saving.set(true);
    const adventureId = this.context.adventure()!.id!;
    if (this.characterId === null) {
      this.create(adventureId, submission);
    } else {
      this.update(adventureId, this.characterId, submission.value);
    }
  }

  private create(adventureId: string, { value, picture }: CharacterSubmission): void {
    createGameCharacter(this.http, this.rootUrl, {
      body: {
        picturePresentation: picture!,
        createGameCharacterRequest: { ...value, adventureId },
      },
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.saving.set(false);
          this.notifier.success('Personnage créé.');
          void this.router.navigate(['/tavern/adventures', adventureId, 'characters', body.id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const failure = toApiProblem(error);
          if (failure.status === 409) {
            this.problem.set(LIMIT_REACHED);
          } else {
            this.onRefused(failure, 'Création du personnage');
          }
        },
      });
  }

  private update(adventureId: string, id: string, body: CharacterValue): void {
    patchGameCharacter(this.http, this.rootUrl, { id, body })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.notifier.success('Personnage enregistré.');
          void this.router.navigate(['/tavern/adventures', adventureId, 'characters', id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const failure = toApiProblem(error);
          if (failure.status === 409) {
            // Keep what is typed until the reader chooses to reload.
            this.notifier.conflict(() => this.fetch(id, () => undefined));
          } else {
            this.onRefused(failure, 'Enregistrement du personnage');
          }
        },
      });
  }

  private onRefused(failure: ApiProblem, context: string): void {
    if (!(failure.status === 400 && this.form()?.showProblem(failure))) {
      this.notifier.error(failure, context);
    }
  }
}
