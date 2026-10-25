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
import { forkJoin, Subscription } from 'rxjs';
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
import { Billing } from '../../../../core/plans/billing.service';
import { PlanCatalog } from '../../../../core/plans/plan-catalog.service';
import { LoadError } from '../../../../shared/ui/load-error/load-error';
import { Loading } from '../../../../shared/ui/loading/loading';
import { AdventureContext } from '../adventure-context.service';
import { CharacterForm, CharacterSubmission, CharacterValue } from './character-form';
import { I18n } from '../../../../core/i18n/i18n.service';

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
    <h1>
      {{ characterId ? t().tavern.characters.editTitle : t().tavern.characters.newTitle }}
    </h1>
    @switch (state()) {
      @case ('ready') {
        @if (!characterId || isOwner()) {
          <dg-character-form
            [initial]="initial()"
            [withPicture]="!characterId"
            [pictureAllowed]="pictureAllowed()"
            [hintScope]="context ? 'adventure' : 'account'"
            [currentPlanId]="planId()"
            [submitLabel]="
              characterId ? t().common.actions.save : t().tavern.characters.createSubmit
            "
            [busy]="saving()"
            [problem]="problem()"
            (saved)="save($event)"
          />
        } @else {
          <div class="state">
            <p role="alert">{{ t().tavern.characters.ownerOnly }}</p>
            <a [routerLink]="backLink()">{{ t().tavern.characters.backToCharacter }}</a>
          </div>
        }
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
export class CharacterFormPage {
  protected readonly t = inject(I18n).t;

  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  /** Null under « Mes personnages »: the character is then created outside any adventure. */
  protected readonly context = inject(AdventureContext, { optional: true });
  private readonly auth = inject(AuthService);
  private readonly notifier = inject(NotifierService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly billing = inject(Billing);
  private readonly catalog = inject(PlanCatalog);

  private readonly form = viewChild(CharacterForm);

  /** Outside an adventure, the reader's own plan; null while unknown. */
  protected readonly planId = signal<string | null>(null);
  private readonly ownPlanHasPictures = signal<boolean | null>(null);
  /**
   * Whether the reader can send a picture. Unknown (loading, or a failed request) counts as yes:
   * the API refuses what the plan does not include and says so.
   */
  protected readonly pictureAllowed = computed(() =>
    this.context === null
      ? (this.ownPlanHasPictures() ?? true)
      : this.context.entitlements() === null || this.context.has('characterPictures'),
  );

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
      this.loadOwnPlan();
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

  private loadOwnPlan(): void {
    if (this.characterId !== null) {
      return;
    }
    forkJoin([this.billing.loadMine(), this.catalog.load()])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ([subscription]) => {
          this.planId.set(subscription.planId ?? null);
          const plan = this.catalog.plan(subscription.planId ?? '');
          this.ownPlanHasPictures.set(plan ? plan.features?.['characterPictures'] === true : null);
        },
        error: () => undefined,
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
            this.notifier.error(toApiProblem(error), this.t().tavern.characters.oneLoading);
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
        ...(picture === null ? {} : { picturePresentation: picture }),
        // Without an adventure, the character is kept outside any.
        createGameCharacterRequest: adventureId === undefined ? value : { ...value, adventureId },
      },
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.saving.set(false);
          this.notifier.success(this.t().tavern.characters.created);
          void this.router.navigate([...this.listLink(), body.id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const failure = toApiProblem(error);
          if (failure.status === 409) {
            this.problem.set(
              adventureId === undefined
                ? this.t().tavern.characters.limitWithoutAdventure
                : this.t().tavern.characters.limitInAdventure,
            );
          } else {
            this.onRefused(failure, this.t().tavern.characters.creating);
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
          this.notifier.success(this.t().tavern.characters.saved);
          void this.router.navigate([...this.listLink(), id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const failure = toApiProblem(error);
          if (failure.status === 409) {
            // Keep what is typed until the reader chooses to reload.
            this.notifier.conflict(() => this.fetch(id, () => undefined));
          } else {
            this.onRefused(failure, this.t().tavern.characters.saving);
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
