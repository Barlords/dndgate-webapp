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
import { Observable } from 'rxjs';
import { AccountDirectory } from '../../../core/accounts/account-directory.service';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  cancelMasterClaim,
  claimMasterRole,
  getMasterClaim,
  inviteMaster,
  revokeMasterInvitation,
  stepDownAsMaster,
  voteOnMasterClaim,
} from '../../../core/api/generated/functions';
import { MasterClaimDto } from '../../../core/api/generated/models/master-claim-dto';
import { MasterInvitationDto } from '../../../core/api/generated/models/master-invitation-dto';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiProblem, toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';
import { Button } from '../../../shared/ui/button/button';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { isClosed, Status } from '../adventure-status';
import { AdventureContext } from './adventure-context.service';

const DATE_TIME = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'short' });
const COPY_FAILED = 'Copie impossible ici : sélectionne le code et copie-le à la main.';

type ClaimState = 'idle' | 'loading' | 'ready' | 'failed';

/**
 * « MJ »: hand the master role over (step down, invitation code) or take it back through a vote
 * when the seat is vacant. Nothing is offered once the adventure is finished or abandoned.
 */
@Component({
  selector: 'dg-master-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadError, Button, Spinner],
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

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-3);
      margin: var(--dg-space-3) 0;
    }

    .code-box {
      margin: var(--dg-space-3) 0;
      padding: var(--dg-space-4);
      color: var(--dg-ink);
      text-align: center;
      background: linear-gradient(180deg, var(--dg-gold-light), var(--dg-gold));
      border: 2px solid var(--dg-gold-deep);
      border-radius: var(--dg-radius);
    }

    .code {
      display: block;
      margin: var(--dg-space-2) 0;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-xl);
      letter-spacing: 0.12em;
      word-break: break-all;
      user-select: all;
    }

    .warning {
      font-style: italic;
    }

    .voters {
      margin: 0 0 var(--dg-space-3);
      padding: 0;
      list-style: none;
    }

    .voters li {
      display: flex;
      justify-content: space-between;
      gap: var(--dg-space-3);
      padding: var(--dg-space-1) 0;
    }
  `,
  template: `
    <h1>Maître du jeu</h1>
    @switch (context.state()) {
      @case ('ready') {
        @if (closed()) {
          <p class="lead">
            Cette aventure est terminée ou abandonnée : le rôle de MJ ne peut plus changer de mains.
          </p>
        } @else if (context.isMaster()) {
          <p class="lead">Tu es le MJ de cette aventure.</p>
          <h2>Inviter un MJ</h2>
          <p>
            Génère un code à usage unique, valable 7 jours, à transmettre à la personne qui
            reprendra le rôle. Un nouveau code remplace l’ancien.
          </p>
          @if (invitation(); as invite) {
            <div class="code-box" role="status">
              <span>Code d’invitation (affiché une seule fois)</span>
              <strong class="code">{{ invite.code }}</strong>
              <span>Valable jusqu’au {{ format(invite.expiresAt) }}</span>
              <div class="actions">
                <dg-button variant="ghost" (click)="copy(invite.code!)">Copier</dg-button>
              </div>
              @if (copyMessage()) {
                <p role="status">{{ copyMessage() }}</p>
              }
              <p class="warning">Générer un nouveau code invalidera celui-ci.</p>
            </div>
          }
          <div class="actions">
            <dg-button [loading]="busy() === 'invite'" (click)="invite()">
              {{ invitation() ? 'Générer un nouveau code' : 'Générer un code' }}
            </dg-button>
            <dg-button variant="ghost" [loading]="busy() === 'revoke'" (click)="revoke()">
              Révoquer l’invitation
            </dg-button>
          </div>
          <h2>Quitter le rôle</h2>
          <p>
            Tu deviendras joueur s’il reste une place. L’aventure n’aura plus de MJ jusqu’à ce qu’un
            joueur reprenne le rôle par un vote, ou qu’un code soit accepté.
          </p>
          <dg-button variant="danger" [loading]="busy() === 'step-down'" (click)="stepDown()">
            Quitter le rôle de MJ
          </dg-button>
        } @else if (context.isPlayer() && context.adventure()!.masterVacant) {
          @switch (claimState()) {
            @case ('ready') {
              @if (claim(); as open) {
                <h2>Demande en cours</h2>
                <p>
                  Candidat : {{ pseudo(open.candidateId!) }}. Tous les autres joueurs doivent
                  accepter avant le {{ format(open.expiresAt) }}, un seul refus y met fin.
                </p>
                <ul class="voters">
                  @for (voter of open.voters ?? []; track voter.accountId) {
                    <li>
                      <span>{{ pseudo(voter.accountId!) }}</span>
                      <span>{{ answer(voter.approved) }}</span>
                    </li>
                  }
                </ul>
                @if (isCandidate()) {
                  <dg-button variant="ghost" [loading]="busy() === 'cancel'" (click)="cancel()">
                    Retirer ma demande
                  </dg-button>
                } @else if (isVoter()) {
                  <p>Ta réponse peut être changée tant que le vote est ouvert.</p>
                  <div class="actions">
                    <dg-button [loading]="busy() === 'approve'" (click)="vote(true)">
                      Accepter
                    </dg-button>
                    <dg-button
                      variant="danger"
                      [loading]="busy() === 'refuse'"
                      (click)="vote(false)"
                    >
                      Refuser
                    </dg-button>
                  </div>
                }
              } @else {
                <p class="lead">Cette aventure n’a plus de MJ.</p>
                <dg-button [loading]="busy() === 'claim'" (click)="claimRole()">
                  Demander à devenir MJ
                </dg-button>
              }
            }
            @case ('failed') {
              <div class="state">
                <dg-load-error
                  message="Impossible de charger le vote."
                  [problem]="loadProblem()"
                  (retry)="loadClaim()"
                />
              </div>
            }
            @default {
              <div class="state"><dg-spinner label="Chargement du vote" /></div>
            }
          }
        } @else {
          <p class="lead">Rien à faire ici pour le moment.</p>
        }
      }
      @case ('loading') {
        <div class="state"><dg-spinner label="Chargement de l’aventure" /></div>
      }
    }
  `,
})
export class MasterPage {
  protected readonly context = inject(AdventureContext);
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly auth = inject(AuthService);
  private readonly directory = inject(AccountDirectory);
  private readonly notifier = inject(NotifierService);
  private readonly confirm = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly invitation = signal<MasterInvitationDto | null>(null);
  protected readonly copyMessage = signal('');
  protected readonly claim = signal<MasterClaimDto | null>(null);
  protected readonly claimState = signal<ClaimState>('idle');
  protected readonly loadProblem = signal<ApiProblem | null>(null);
  protected readonly busy = signal<string | null>(null);

  protected readonly closed = computed(() => {
    const adventure = this.context.adventure();
    return !!adventure && isClosed(adventure.status as Status);
  });
  private readonly me = computed(() => this.auth.account()?.id);
  protected readonly isCandidate = computed(() => this.claim()?.candidateId === this.me());
  protected readonly isVoter = computed(() =>
    (this.claim()?.voters ?? []).some((voter) => voter.accountId === this.me()),
  );

  constructor() {
    // The vote only exists while the seat is vacant.
    effect(() => {
      const wanted =
        this.context.state() === 'ready' &&
        this.context.isPlayer() &&
        !!this.context.adventure()?.masterVacant &&
        !this.closed();
      untracked(() => {
        if (wanted) {
          this.loadClaim();
        } else {
          this.claim.set(null);
          this.claimState.set('idle');
        }
      });
    });
  }

  protected format(iso: string | undefined): string {
    return iso ? DATE_TIME.format(new Date(iso)) : '';
  }

  protected pseudo(id: string): string {
    return this.directory.pseudo(id)();
  }

  protected answer(approved: boolean | null | undefined): string {
    if (approved === true) {
      return 'Accepte';
    }
    return approved === false ? 'Refuse' : 'En attente';
  }

  protected loadClaim(): void {
    this.claimState.set('loading');
    getMasterClaim(this.http, this.rootUrl, { id: this.adventureId() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => this.showClaim(body),
        error: (error: unknown) => {
          // 404: no vote in progress.
          const problem = toApiProblem(error);
          if (problem.status === 404) {
            this.claim.set(null);
            this.claimState.set('ready');
          } else {
            this.loadProblem.set(problem);
            this.claimState.set('failed');
          }
        },
      });
  }

  protected invite(): void {
    this.run('invite', inviteMaster(this.http, this.rootUrl, { id: this.adventureId() }), (r) => {
      this.copyMessage.set('');
      this.invitation.set(r.body);
    });
  }

  protected revoke(): void {
    this.run(
      'revoke',
      revokeMasterInvitation(this.http, this.rootUrl, { id: this.adventureId() }),
      () => {
        this.invitation.set(null);
        this.notifier.success('L’invitation est révoquée.');
      },
    );
  }

  protected copy(code: string): void {
    const clipboard = typeof navigator === 'undefined' ? undefined : navigator.clipboard;
    if (!clipboard) {
      this.copyMessage.set(COPY_FAILED);
      return;
    }
    clipboard.writeText(code).then(
      () => this.copyMessage.set('Code copié.'),
      () => this.copyMessage.set(COPY_FAILED),
    );
  }

  protected stepDown(): void {
    const name = this.context.adventure()!.name!;
    this.confirm
      .typeToConfirm({
        title: 'Quitter le rôle de MJ',
        message: 'Retape le nom de l’aventure pour confirmer. Les joueurs seront prévenus.',
        expected: name,
        inputLabel: 'Nom de l’aventure',
        inputType: 'text',
        confirmLabel: 'Quitter le rôle de MJ',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmation) => {
        if (confirmation === null) {
          return;
        }
        const request = stepDownAsMaster(this.http, this.rootUrl, {
          id: this.adventureId(),
          body: { confirmation },
        });
        this.run('step-down', request, () => {
          this.invitation.set(null);
          this.notifier.success('Tu n’es plus le MJ de cette aventure.');
          this.context.reload();
        });
      });
  }

  protected claimRole(): void {
    this.run('claim', claimMasterRole(this.http, this.rootUrl, { id: this.adventureId() }), (r) =>
      this.showClaim(r.body),
    );
  }

  protected cancel(): void {
    this.run(
      'cancel',
      cancelMasterClaim(this.http, this.rootUrl, { id: this.adventureId() }),
      () => {
        this.claim.set(null);
        this.notifier.success('Ta demande est retirée.');
      },
    );
  }

  protected vote(approve: boolean): void {
    const request = voteOnMasterClaim(this.http, this.rootUrl, {
      id: this.adventureId(),
      body: { approve },
    });
    this.run(approve ? 'approve' : 'refuse', request, (r) => this.showClaim(r.body));
  }

  /** An open vote is shown; any other outcome ended it (and maybe moved the seat), so reload. */
  private showClaim(claim: MasterClaimDto): void {
    if (claim.status === 'OPEN') {
      this.claim.set(claim);
    } else {
      this.claim.set(null);
      if (claim.status === 'ACCEPTED') {
        this.notifier.success('Le rôle de MJ a changé de mains.');
      }
      this.context.reload();
    }
    this.claimState.set('ready');
  }

  private adventureId(): string {
    return this.context.adventure()!.id!;
  }

  private run<T>(key: string, request: Observable<T>, done: (value: T) => void): void {
    if (this.busy() !== null) {
      return;
    }
    this.busy.set(key);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (value) => {
        this.busy.set(null);
        done(value);
      },
      error: (error: unknown) => {
        this.busy.set(null);
        this.fail(toApiProblem(error));
      },
    });
  }

  private fail(problem: ApiProblem): void {
    if (problem.status === 409) {
      // The state changed meanwhile: say so and show the current one.
      this.notifier.conflict(() => undefined);
      this.context.reload();
      if (this.claimState() !== 'idle') {
        this.loadClaim();
      }
    } else {
      this.notifier.error(problem, 'Rôle de MJ');
    }
  }
}
