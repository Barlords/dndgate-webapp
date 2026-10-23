import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { catchError, EMPTY, forkJoin, map, Subject, switchMap } from 'rxjs';
import { AccountDirectory, displayName } from '../../core/accounts/account-directory.service';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { getMeanRateOfAccount, getRatingsByAccount } from '../../core/api/generated/functions';
import { PageDtoRatingDto } from '../../core/api/generated/models/page-dto-rating-dto';
import { PublicAccountDto } from '../../core/api/generated/models/public-account-dto';
import { RatingMeanDto } from '../../core/api/generated/models/rating-mean-dto';
import { AuthService } from '../../core/auth/auth.service';
import { toApiProblem } from '../../core/errors/api-problem';
import { NotifierService } from '../../core/errors/notifier.service';
import { Paginator } from '../../shared/ui/paginator/paginator';
import { Loading } from '../../shared/ui/loading/loading';
import { StarRating } from '../../shared/ui/star-rating/star-rating';
import { AccountLayout } from './account-layout';

const PAGE_SIZE = 10;

interface Profile {
  account: PublicAccountDto;
  mean: RatingMeanDto;
}

@Component({
  selector: 'dg-public-profile-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AccountLayout, Loading, StarRating, Paginator, DatePipe],
  styles: `
    .summary {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dg-space-2);

      p {
        margin: 0;
      }
    }

    h2 {
      margin: var(--dg-space-5) 0 var(--dg-space-3);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-md);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--dg-title-color);
    }

    ul {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--dg-space-3);
      padding: var(--dg-space-2) 0;
      border-bottom: 1px solid var(--dg-parchment-border);
    }

    small {
      margin-left: var(--dg-space-2);
    }
  `,
  template: `
    <dg-account-layout [heading]="heading()">
      @if (profile(); as shown) {
        <div class="summary">
          @if (shown.mean.average === null) {
            <p>Pas encore d'avis</p>
          } @else {
            <dg-star-rating [value]="shown.mean.average" [readonly]="true" />
            <p>{{ shown.mean.count }} avis</p>
          }
        </div>
        @if (mine() && ratings(); as page) {
          <h2>Avis reçus</h2>
          <ul>
            @for (rating of page.content ?? []; track rating.id) {
              <li>
                <span>
                  {{ author(rating.emitterId) }}
                  <small>{{ rating.createdAt | date: 'dd/MM/yyyy' }}</small>
                </span>
                <dg-star-rating [value]="rating.rate ?? null" [readonly]="true" />
              </li>
            }
          </ul>
          <dg-paginator
            [page]="page.page ?? 0"
            [totalPages]="page.totalPages ?? 0"
            (pageChange)="pages.next($event)"
          />
        }
      } @else if (failed()) {
        <p role="alert">Ce joueur est introuvable.</p>
      } @else {
        <div class="dg-account__actions">
          <dg-loading
            label="Chargement du joueur"
            message="On cherche ce joueur dans la taverne…"
          />
        </div>
      }
    </dg-account-layout>
  `,
})
export class PublicProfilePage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly auth = inject(AuthService);
  private readonly directory = inject(AccountDirectory);
  private readonly notifier = inject(NotifierService);

  private readonly accountId = signal('');
  protected readonly profile = signal<Profile | null>(null);
  protected readonly failed = signal(false);
  protected readonly ratings = signal<PageDtoRatingDto | null>(null);
  protected readonly pages = new Subject<number>();

  protected readonly heading = computed(() => {
    const account = this.profile()?.account;
    return account === undefined ? 'Joueur' : displayName(account);
  });
  protected readonly mine = computed(() => this.auth.account()?.id === this.accountId());

  constructor() {
    const destroyRef = inject(DestroyRef);

    inject(ActivatedRoute)
      .paramMap.pipe(
        map((params) => params.get('accountId') ?? ''),
        switchMap((id) => {
          this.accountId.set(id);
          this.profile.set(null);
          this.ratings.set(null);
          this.failed.set(false);
          return forkJoin({
            account: this.directory.get(id),
            mean: getMeanRateOfAccount(this.http, this.rootUrl, { accountId: id }).pipe(
              map((response) => response.body),
            ),
          }).pipe(
            // An error here must not end the stream: the component is reused for the next id.
            catchError((error: unknown) => {
              const problem = toApiProblem(error);
              this.failed.set(true);
              if (problem.status !== 404) {
                this.notifier.error(problem, 'Chargement du joueur');
              }
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((profile) => {
        this.profile.set(profile);
        if (this.mine()) {
          this.pages.next(0);
        }
      });

    this.pages
      .pipe(
        switchMap((page) =>
          getRatingsByAccount(this.http, this.rootUrl, {
            targetedAccountId: this.accountId(),
            page,
            size: PAGE_SIZE,
          }).pipe(
            // A failed page must not end the pager: the reader can try another one.
            catchError((error: unknown) => {
              this.notifier.error(toApiProblem(error), 'Chargement des avis');
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((response) => this.ratings.set(response.body));
  }

  protected author(id: string | undefined): string {
    return id === undefined ? '' : this.directory.pseudo(id)();
  }
}
