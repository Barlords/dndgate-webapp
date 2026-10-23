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
import { NgTemplateOutlet } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AccountDirectory } from '../../../core/accounts/account-directory.service';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  createPlanning,
  getAvailabilitiesByPlanningId,
  getPlanningByAdventureId,
  putAvailability,
  putPlanning,
} from '../../../core/api/generated/functions';
import { AvailabilityDto } from '../../../core/api/generated/models/availability-dto';
import { PlanningDto } from '../../../core/api/generated/models/planning-dto';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiProblem, toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';
import { Button } from '../../../shared/ui/button/button';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { DatePicker } from '../../../shared/ui/date-picker/date-picker';
import { compareIso, formatDate } from '../../../shared/ui/date-picker/iso-date';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { AdventureContext } from './adventure-context.service';

type PlanningState = 'loading' | 'none' | 'ready' | 'failed';

/**
 * « Planning »: the dates the master proposes and what the participants can make. Anyone who can
 * see the adventure sees the dates; the votes are for the participants only.
 */
@Component({
  selector: 'dg-planning-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Button, Spinner, DatePicker, NgTemplateOutlet],
  styleUrl: '../tavern-page.scss',
  styles: `
    h2 {
      margin: var(--dg-space-5) 0 var(--dg-space-3);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-lg);
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--dg-title-color);
    }

    .proposed,
    .choices {
      margin: 0 0 var(--dg-space-4);
      padding: 0;
      list-style: none;
    }

    .proposed li {
      padding: var(--dg-space-1) 0;
    }

    .choices label {
      display: flex;
      align-items: center;
      gap: var(--dg-space-3);
      min-height: 2.5rem;
      cursor: pointer;
    }

    .choices input {
      width: 1.25rem;
      height: 1.25rem;
      accent-color: var(--dg-blood);
    }

    .problem {
      margin: 0 0 var(--dg-space-3);
      color: var(--dg-blood);
      font-weight: 600;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-3);
      margin-top: var(--dg-space-3);
    }

    .scroll {
      overflow-x: auto;
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
    }

    table {
      width: 100%;
      border-collapse: collapse;
      color: var(--dg-ink);
      background: linear-gradient(180deg, var(--dg-parchment-0), var(--dg-parchment-1));
    }

    caption {
      padding: var(--dg-space-2) var(--dg-space-3);
      font-family: var(--dg-font-title);
      text-align: left;
      color: var(--dg-ink-muted);
    }

    th,
    td {
      padding: var(--dg-space-2) var(--dg-space-3);
      text-align: center;
      white-space: nowrap;
      border-top: 1px solid var(--dg-parchment-shadow);
    }

    thead th {
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      font-weight: 500;
    }

    tbody th,
    tfoot th {
      position: sticky;
      left: 0;
      text-align: left;
      background: var(--dg-parchment-0);
      box-shadow: 1px 0 0 var(--dg-parchment-shadow);
    }

    tfoot {
      font-weight: 700;
    }

    .best {
      color: var(--dg-ink);
      background: var(--dg-gold-light);
      box-shadow: inset 0 0 0 1px var(--dg-gold-deep);
    }

    .yes {
      color: var(--dg-blood);
      font-weight: 700;
    }

    .sr {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
    }
  `,
  template: `
    <h1>Planning</h1>
    <ng-template #editor>
      <p class="lead">Choisis les dates que tu proposes à la table (100 au plus).</p>
      <dg-date-picker mode="multiple" [selected]="draft()" (selectedChange)="draft.set($event)" />
      @if (draftProblem()) {
        <p class="problem" role="alert">Choisis au moins une date.</p>
      }
      <div class="actions">
        <dg-button [loading]="savingPlanning()" (click)="savePlanning()">
          Enregistrer les dates
        </dg-button>
        <dg-button variant="ghost" (click)="editing.set(false)">Annuler</dg-button>
      </div>
    </ng-template>
    @switch (state()) {
      @case ('ready') {
        @let plan = planning()!;
        @if (editing()) {
          <ng-container *ngTemplateOutlet="editor" />
        } @else {
          <h2>Dates proposées</h2>
          <ul class="proposed">
            @for (date of dates(); track date) {
              <li>{{ format(date) }}</li>
            }
          </ul>
          @if (context.isMaster()) {
            <dg-button variant="ghost" (click)="edit()">Modifier les dates</dg-button>
          }
        }

        @if (context.isParticipant() && !editing()) {
          <h2>Mes disponibilités</h2>
          @if (votesState() === 'ready') {
            <p class="lead">Coche les dates où tu es disponible.</p>
            <ul class="choices">
              @for (date of dates(); track date) {
                <li>
                  <label>
                    <input
                      type="checkbox"
                      [checked]="mine().includes(date)"
                      (change)="toggle(date)"
                    />
                    {{ format(date) }}
                  </label>
                </li>
              }
            </ul>
            @if (voteProblem()) {
              <p class="problem" role="alert">Coche au moins une date.</p>
            }
            <dg-button [loading]="voting()" (click)="vote(plan)">
              Enregistrer mes disponibilités
            </dg-button>

            <h2>Disponibilités de la table</h2>
            @if (votes().length) {
              <div
                class="scroll"
                tabindex="0"
                role="region"
                aria-label="Disponibilités de la table"
              >
                <table>
                  <caption class="sr">
                    Disponibilités par joueur et par date
                  </caption>
                  <thead>
                    <tr>
                      <th scope="col">Joueur</th>
                      @for (date of dates(); track date) {
                        <th scope="col" [class.best]="best().has(date)">{{ format(date) }}</th>
                      }
                    </tr>
                  </thead>
                  <tbody>
                    @for (entry of votes(); track entry.id) {
                      <tr>
                        <th scope="row">{{ pseudo(entry.accountId!) }}</th>
                        @for (date of dates(); track date) {
                          @if (entry.availabilityDates?.includes(date)) {
                            <td class="yes">
                              <span aria-hidden="true">✓</span><span class="sr">Disponible</span>
                            </td>
                          } @else {
                            <td>
                              <span aria-hidden="true">·</span><span class="sr">Indisponible</span>
                            </td>
                          }
                        }
                      </tr>
                    }
                  </tbody>
                  <tfoot>
                    <tr>
                      <th scope="row">Total</th>
                      @for (date of dates(); track date) {
                        <td [class.best]="best().has(date)">
                          {{ totals().get(date) }}
                          @if (best().has(date)) {
                            <span class="sr">Meilleure date</span>
                          }
                        </td>
                      }
                    </tr>
                  </tfoot>
                </table>
              </div>
            } @else {
              <p>Personne n’a encore donné ses disponibilités.</p>
            }
          } @else if (votesState() === 'failed') {
            <div class="state">
              <dg-load-error
                message="Impossible de charger les disponibilités."
                [problem]="votesProblem()"
                (retry)="loadVotes(plan)"
              />
            </div>
          } @else {
            <div class="state"><dg-spinner label="Chargement des disponibilités" /></div>
          }
        }
      }
      @case ('none') {
        <div class="state">
          <p>Aucune date proposée pour le moment.</p>
        </div>
        @if (context.isMaster()) {
          @if (editing()) {
            <ng-container *ngTemplateOutlet="editor" />
          } @else {
            <dg-button (click)="edit()">Proposer des dates</dg-button>
          }
        }
      }
      @case ('failed') {
        <div class="state">
          <dg-load-error
            message="Impossible de charger le planning."
            [problem]="loadProblem()"
            (retry)="loadPlanning()"
          />
        </div>
      }
      @default {
        <div class="state"><dg-spinner label="Chargement du planning" /></div>
      }
    }
  `,
})
export class PlanningPage {
  protected readonly context = inject(AdventureContext);
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly auth = inject(AuthService);
  private readonly directory = inject(AccountDirectory);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<PlanningState>('loading');
  protected readonly planning = signal<PlanningDto | null>(null);
  protected readonly votesState = signal<'loading' | 'ready' | 'failed'>('loading');
  protected readonly loadProblem = signal<ApiProblem | null>(null);
  protected readonly votesProblem = signal<ApiProblem | null>(null);
  protected readonly votes = signal<AvailabilityDto[]>([]);
  protected readonly mine = signal<string[]>([]);

  protected readonly editing = signal(false);
  protected readonly draft = signal<string[]>([]);
  protected readonly draftProblem = signal(false);
  protected readonly savingPlanning = signal(false);
  protected readonly voting = signal(false);
  protected readonly voteProblem = signal(false);

  protected readonly dates = computed(() =>
    [...(this.planning()?.propositionDates ?? [])].sort(compareIso),
  );

  protected readonly totals = computed(() => {
    const totals = new Map(this.dates().map((date) => [date, 0]));
    for (const entry of this.votes()) {
      for (const date of new Set(entry.availabilityDates ?? [])) {
        if (totals.has(date)) {
          totals.set(date, totals.get(date)! + 1);
        }
      }
    }
    return totals;
  });

  /** The date(s) the most participants can make; none while nobody can make any. */
  protected readonly best = computed(() => {
    const totals = this.totals();
    const top = Math.max(0, ...totals.values());
    return new Set(top === 0 ? [] : [...totals].filter(([, n]) => n === top).map(([d]) => d));
  });

  private requested = false;

  constructor() {
    effect(() => {
      if (this.context.state() === 'ready') {
        untracked(() => {
          if (!this.requested) {
            this.requested = true;
            this.loadPlanning();
          }
        });
      }
    });
  }

  protected format(date: string): string {
    return formatDate(date);
  }

  protected pseudo(id: string): string {
    return this.directory.pseudo(id)();
  }

  protected loadPlanning(): void {
    this.state.set('loading');
    getPlanningByAdventureId(this.http, this.rootUrl, {
      adventureId: this.context.adventure()!.id!,
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => this.show(body),
        error: (error: unknown) => {
          // 404: nobody has proposed dates yet.
          const problem = toApiProblem(error);
          this.loadProblem.set(problem);
          this.state.set(problem.status === 404 ? 'none' : 'failed');
        },
      });
  }

  private show(planning: PlanningDto): void {
    this.planning.set(planning);
    this.state.set('ready');
    this.editing.set(false);
    if (this.context.isParticipant()) {
      this.loadVotes(planning);
    }
  }

  protected loadVotes(planning: PlanningDto): void {
    this.votesState.set('loading');
    getAvailabilitiesByPlanningId(this.http, this.rootUrl, { planningId: planning.id! })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          const me = this.auth.account()?.id;
          this.votes.set(body);
          this.mine.set([...(body.find((v) => v.accountId === me)?.availabilityDates ?? [])]);
          this.votesState.set('ready');
        },
        error: (error: unknown) => {
          this.votesProblem.set(toApiProblem(error));
          this.votesState.set('failed');
        },
      });
  }

  protected edit(): void {
    this.draft.set([...this.dates()]);
    this.draftProblem.set(false);
    this.editing.set(true);
  }

  protected savePlanning(): void {
    const propositionDates = this.draft();
    if (propositionDates.length === 0) {
      this.draftProblem.set(true);
      return;
    }
    this.draftProblem.set(false);
    this.savingPlanning.set(true);
    const body = { adventureId: this.context.adventure()!.id!, propositionDates };
    const call =
      this.planning() === null
        ? createPlanning(this.http, this.rootUrl, { body })
        : putPlanning(this.http, this.rootUrl, { body });
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ body: saved }) => {
        this.savingPlanning.set(false);
        this.notifier.success('Dates enregistrées.');
        this.show(saved);
      },
      error: (error: unknown) => {
        this.savingPlanning.set(false);
        this.notifier.error(toApiProblem(error), 'Enregistrement des dates');
      },
    });
  }

  protected toggle(date: string): void {
    this.voteProblem.set(false);
    this.mine.update((dates) =>
      dates.includes(date) ? dates.filter((d) => d !== date) : [...dates, date],
    );
  }

  protected vote(planning: PlanningDto): void {
    const propositionDates = this.dates().filter((date) => this.mine().includes(date));
    if (propositionDates.length === 0) {
      this.voteProblem.set(true);
      return;
    }
    this.voting.set(true);
    putAvailability(this.http, this.rootUrl, {
      body: { planningId: planning.id!, propositionDates },
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.voting.set(false);
          this.notifier.success('Disponibilités enregistrées.');
          this.loadVotes(planning);
        },
        error: (error: unknown) => {
          this.voting.set(false);
          this.notifier.error(toApiProblem(error), 'Enregistrement des disponibilités');
        },
      });
  }
}
