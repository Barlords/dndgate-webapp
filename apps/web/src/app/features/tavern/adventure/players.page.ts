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
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccountDirectory } from '../../../core/accounts/account-directory.service';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import { invitePlayer } from '../../../core/api/generated/functions';
import { toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';
import { Button } from '../../../shared/ui/button/button';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { AdventureContext } from './adventure-context.service';

/** « Joueurs »: the master, then the players; the master of a new adventure can invite more. */
@Component({
  selector: 'dg-adventure-players-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ReactiveFormsModule, Button, FormField],
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

    .problem {
      margin: 0 0 var(--dg-space-3);
      color: var(--dg-blood);
      font-weight: 600;
    }
  `,
  template: `
    @let a = context.adventure()!;
    <h1>Joueurs</h1>
    <ul class="people">
      <li>
        <span class="role">MJ</span>
        @if (a.masterVacant) {
          Aucun MJ pour le moment.
        } @else {
          <a [routerLink]="['/players', a.creatorId]">{{ pseudo(a.creatorId!) }}</a>
        }
      </li>
      @for (id of a.players; track id) {
        <li>
          <span class="role">Joueur</span>
          <a [routerLink]="['/players', id]">{{ pseudo(id) }}</a>
        </li>
      } @empty {
        <li>Aucun joueur pour le moment.</li>
      }
    </ul>

    @if (canInvite()) {
      <h2>Inviter un joueur</h2>
      <form [formGroup]="form" (ngSubmit)="invite()" novalidate>
        <dg-form-field label="Pseudo du joueur" [control]="pseudoControl">
          <input type="text" formControlName="pseudo" autocomplete="off" />
        </dg-form-field>
        @if (problem(); as message) {
          <p class="problem" role="alert">{{ message }}</p>
        }
        <dg-button type="submit" [loading]="inviting()">Inviter</dg-button>
      </form>
    }
  `,
})
export class PlayersPage {
  protected readonly context = inject(AdventureContext);
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly directory = inject(AccountDirectory);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

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
          this.notifier.success(`Invitation envoyée à ${pseudo}.`);
        },
        error: (error: unknown) => {
          this.inviting.set(false);
          const failure = toApiProblem(error);
          if (failure.status === 409) {
            this.problem.set('Cette aventure a déjà commencé ou ce joueur y participe déjà.');
          } else if (failure.status === 429) {
            const wait = failure.retryAfterSeconds;
            this.problem.set(
              wait === undefined
                ? 'Trop d’invitations envoyées, réessaie plus tard.'
                : `Trop d’invitations envoyées, réessaie dans ${wait} s.`,
            );
          } else {
            this.notifier.error(failure, 'Invitation du joueur');
          }
        },
      });
  }
}
