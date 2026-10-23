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
import { LoadError } from '../../../../shared/ui/load-error/load-error';
import { Loading } from '../../../../shared/ui/loading/loading';
import { AdventureContext } from '../adventure-context.service';
import { CharacterForm, CharacterSubmission, CharacterValue } from './character-form';

const LIMIT_REACHED = 'Tu as déjà 10 personnages dans cette aventure.';
const LIMIT_REACHED_WITHOUT_ADVENTURE = 'Tu as déjà 50 personnages hors aventure.';

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

/**
 * Creation of a character (`characters/new`) and edition of one of the reader's (`…/edit`), in an
 * adventure or, under « Mes personnages », outside any (a character played at a real table).
 */
@Component({
  selector: 'dg-character-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, CharacterForm, RouterLink, Loading],
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
          <dg-load-error
            message="Impossible de charger le personnage."
            [problem]="loadProblem()"
            (retry)="load()"
          />
        </div>
      }
      @default {
        <div class="state">
          <dg-loading label="Chargement du personnage" message="Le héros ajuste son équipement…" />
        </div>
      }
    }
  `,
})
export class CharacterFormPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  /** Null under « Mes personnages »: the character is then created outside any adventure. */
  private readonly context = inject(AdventureContext, { optional: true });
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
  protected readonly loadProblem = signal<ApiProblem | null>(null);
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

  protected readonly listLink = computed(() =>
    this.context === null
      ? ['/tavern/characters']
      : ['/tavern/adventures', this.context.adventure()?.id, 'characters'],
  );
  protected readonly backLink = computed(() => [...this.listLink(), this.characterId]);

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
          this.breadcrumb.setLabelAt([...this.listLink(), id].join('/'), body.name ?? '');
          done(body);
        },
        error: (error: unknown) => {
          // A failed reload keeps what is shown.
          if (this.loaded() === null) {
            const problem = toApiProblem(error);
            this.loadProblem.set(problem);
            this.state.set(problem.status === 404 ? 'missing' : 'failed');
          } else {
            this.notifier.error(toApiProblem(error), 'Chargement du personnage');
          }
        },
      });
  }

  protected save(submission: CharacterSubmission): void {
    this.problem.set(null);
    this.saving.set(true);
    if (this.characterId === null) {
      this.create(this.context?.adventure()?.id, submission);
    } else {
      this.update(this.characterId, submission.value);
    }
  }

  private create(adventureId: string | undefined, { value, picture }: CharacterSubmission): void {
    createGameCharacter(this.http, this.rootUrl, {
      body: {
        picturePresentation: picture!,
        // Without an adventure, the character is kept outside any.
        createGameCharacterRequest: adventureId === undefined ? value : { ...value, adventureId },
      },
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.saving.set(false);
          this.notifier.success('Personnage créé.');
          void this.router.navigate([...this.listLink(), body.id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const failure = toApiProblem(error);
          if (failure.status === 409) {
            this.problem.set(
              adventureId === undefined ? LIMIT_REACHED_WITHOUT_ADVENTURE : LIMIT_REACHED,
            );
          } else {
            this.onRefused(failure, 'Création du personnage');
          }
        },
      });
  }

  private update(id: string, body: CharacterValue): void {
    patchGameCharacter(this.http, this.rootUrl, { id, body })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.notifier.success('Personnage enregistré.');
          void this.router.navigate([...this.listLink(), id]);
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
