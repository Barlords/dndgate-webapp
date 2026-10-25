import { NgTemplateOutlet } from '@angular/common';
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
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccountDirectory } from '../../../core/accounts/account-directory.service';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  getMeanRateOfAccount,
  getRatingBetween,
  invitePlayer,
  putRating,
} from '../../../core/api/generated/functions';
import { RatingMeanDto } from '../../../core/api/generated/models/rating-mean-dto';
import { AuthService } from '../../../core/auth/auth.service';
import { toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';
import { Button } from '../../../shared/ui/button/button';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { StarRating } from '../../../shared/ui/star-rating/star-rating';
import { AdventureContext } from './adventure-context.service';
import { I18n } from '../../../core/i18n/i18n.service';

/** « Joueurs »: the master, then the players; the master of a new adventure can invite more. */
@Component({
  selector: 'dg-adventure-players-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, RouterLink, ReactiveFormsModule, Button, FormField, StarRating],
  styleUrl: '../tavern-page.scss',
  styles: `
    .people {
      margin: 0 0 var(--dg-space-4);
      padding: 0;
      list-style: none;
    }

    .role {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--dg-ink-muted);
    }

    .rating {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--dg-space-3);
      margin-top: var(--dg-space-1);
    }

    .mean {
      color: var(--dg-ink-muted);
    }

    .problem {
      margin: 0 0 var(--dg-space-3);
      color: var(--dg-blood);
      font-weight: 600;
    }
  `,
  template: `
    @let a = context.adventure()!;
    <h1>{{ t().tavern.players.title }}</h1>
    <ul class="people">
      <li>
        <span class="role">{{ t().tavern.roles.master }}</span>
        @if (a.masterVacant) {
          {{ t().tavern.players.noMaster }}
        } @else {
          <a [routerLink]="['/players', a.creatorId]">{{ pseudo(a.creatorId!) }}</a>
          <ng-container *ngTemplateOutlet="ratingTpl; context: { id: a.creatorId }" />
        }
      </li>
      @for (id of a.players; track id) {
        <li>
          <span class="role">{{ t().tavern.roles.player }}</span>
          <a [routerLink]="['/players', id]">{{ pseudo(id) }}</a>
          <ng-container *ngTemplateOutlet="ratingTpl; context: { id }" />
        </li>
      } @empty {
        <li>{{ t().tavern.players.none }}</li>
      }
    </ul>

    <ng-template #ratingTpl let-id="id">
      @if (canRate(id)) {
        <div class="rating">
          <span>{{ t().tavern.players.yourRating }}</span>
          <dg-star-rating [value]="mine()[id]" (valueChange)="rate(id, $event)" />
          @if (means()[id]; as m) {
            <span class="mean">{{ meanLabel(m) }}</span>
          }
        </div>
      }
    </ng-template>

    @if (canInvite()) {
      <h2>{{ t().tavern.players.invite }}</h2>
      <form [formGroup]="form" (ngSubmit)="invite()" novalidate>
        <dg-form-field [label]="t().tavern.players.pseudo" [control]="pseudoControl">
          <input type="text" formControlName="pseudo" autocomplete="off" />
        </dg-form-field>
        @if (problem(); as message) {
          <p class="problem" role="alert">{{ message }}</p>
        }
        <dg-button type="submit" [loading]="inviting()">{{
          t().tavern.players.inviteSubmit
        }}</dg-button>
      </form>
    }
  `,
})
export class PlayersPage {
  protected readonly t = inject(I18n).t;

  protected readonly context = inject(AdventureContext);
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly directory = inject(AccountDirectory);
  private readonly notifier = inject(NotifierService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  /** My rate of each rateable participant (null: not rated yet); an id is present once looked up. */
  protected readonly mine = signal<Record<string, number | null>>({});
  protected readonly means = signal<Record<string, RatingMeanDto>>({});
  private readonly looked = new Set<string>();

  protected readonly pseudoControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  protected readonly form = new FormGroup({ pseudo: this.pseudoControl });
  protected readonly inviting = signal(false);
  protected readonly problem = signal<string | null>(null);

  protected readonly canInvite = computed(
    () => this.context.isMaster() && this.context.adventure()?.status === 'NOT_STARTED',
  );

  constructor() {
    effect(() => {
      const a = this.context.adventure();
      const me = this.auth.account()?.id;
      const started = a?.status === 'ONGOING' || a?.status === 'FINISH';
      if (!a || me === undefined || !started || !this.context.isParticipant()) {
        return;
      }
      const others = [a.masterVacant ? undefined : a.creatorId, ...(a.players ?? [])];
      untracked(() => {
        for (const id of others) {
          if (id !== undefined && id !== me && !this.looked.has(id)) {
            this.looked.add(id);
            this.lookUp(id, me);
          }
        }
      });
    });
  }

  protected canRate(id: string): boolean {
    return id in this.mine();
  }

  protected meanLabel(mean: RatingMeanDto): string {
    const texts = this.t().tavern.players;
    if (mean.average === null) {
      return texts.noRatingYet;
    }
    const average = mean.average.toLocaleString(this.t().common.locale, {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    });
    return texts.mean(average, mean.count ?? 0);
  }

  protected rate(id: string, rate: number): void {
    putRating(this.http, this.rootUrl, { body: { accountTargetedId: id, rate } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.mine.update((all) => ({ ...all, [id]: rate }));
          this.loadMean(id);
        },
        error: (error: unknown) =>
          this.notifier.error(toApiProblem(error), this.t().tavern.players.rating),
      });
  }

  /** Anonymized accounts cannot be rated: only the others get their rate and mean fetched. */
  private lookUp(id: string, me: string): void {
    this.directory
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (account) => {
          if (account.anonymized) {
            return;
          }
          this.loadMine(id, me);
          this.loadMean(id);
        },
        // The name lookup already shows « Compte inconnu »: just do not offer a rating.
        error: () => undefined,
      });
  }

  private loadMine(id: string, me: string): void {
    getRatingBetween(this.http, this.rootUrl, { emitterId: me, targetedAccountId: id })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) =>
          this.mine.update((all) => ({ ...all, [id]: response.body.rate ?? null })),
        error: (error: unknown) => {
          const failure = toApiProblem(error);
          this.mine.update((all) => ({ ...all, [id]: null }));
          if (failure.status !== 404) {
            this.notifier.error(failure, this.t().tavern.players.loadingRating);
          }
        },
      });
  }

  private loadMean(id: string): void {
    getMeanRateOfAccount(this.http, this.rootUrl, { accountId: id })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.means.update((all) => ({ ...all, [id]: response.body })),
        // The mean is a bonus: without it the person can still be rated.
        error: () => undefined,
      });
  }

  protected pseudo(id: string): string {
    return this.directory.pseudo(id)();
  }

  protected invite(): void {
    const pseudo = this.pseudoControl.value.trim();
    if (this.inviting()) {
      return;
    }
    if (pseudo === '') {
      this.pseudoControl.markAsTouched();
      return;
    }
    this.problem.set(null);
    this.inviting.set(true);
    invitePlayer(this.http, this.rootUrl, { id: this.context.adventure()!.id!, pseudo })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.inviting.set(false);
          this.pseudoControl.reset('');
          this.notifier.success(this.t().tavern.players.invited(pseudo));
        },
        error: (error: unknown) => {
          this.inviting.set(false);
          const failure = toApiProblem(error);
          if (failure.status === 409) {
            this.problem.set(this.t().tavern.players.cannotInvite);
          } else if (failure.status === 429) {
            const wait = failure.retryAfterSeconds;
            this.problem.set(
              wait === undefined
                ? this.t().tavern.players.tooManyInvitations
                : this.t().tavern.players.tooManyInvitationsFor(wait),
            );
          } else {
            this.notifier.error(failure, this.t().tavern.players.inviting);
          }
        },
      });
  }
}
