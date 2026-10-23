import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { filter, Observable } from 'rxjs';
import { AccountDirectory } from '../../../core/accounts/account-directory.service';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  deleteAdventureById,
  joinAdventure,
  quitAdventure,
  updateAdventure,
  updateAdventureStatus,
  updateAdventureVisibility,
} from '../../../core/api/generated/functions';
import { AdventureDto } from '../../../core/api/generated/models/adventure-dto';
import { ApiProblem, toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';
import { Button } from '../../../shared/ui/button/button';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { AdventureForm, AdventureValue } from '../adventure-form';
import {
  allowedTransitions,
  isClosed,
  STATUS_LABEL,
  Status,
  VISIBILITY_LABEL,
} from '../adventure-status';
import { AdventureContext } from './adventure-context.service';

const STATUS_ACTION: Record<Status, string> = {
  NOT_STARTED: 'Remettre à plus tard',
  ONGOING: 'Démarrer l’aventure',
  FINISH: 'Terminer l’aventure',
  ABORT: 'Abandonner l’aventure',
};

const IRREVERSIBLE_MESSAGE: Partial<Record<Status, string>> = {
  FINISH: 'Terminer l’aventure est irréversible : elle ne pourra plus être rouverte.',
  ABORT: 'Abandonner l’aventure est irréversible : elle ne pourra plus être rouverte.',
};

function toValue(adventure: AdventureDto): AdventureValue {
  return {
    name: adventure.name ?? '',
    description: adventure.description ?? '',
    maxPlayer: adventure.maxPlayer ?? 1,
    visibility: adventure.visibility ?? 'PUBLIC',
    postalCode: adventure.postalCode ?? '',
    city: adventure.city ?? '',
  };
}

/** « Informations »: the adventure, and for its master the means to change, run or delete it. */
@Component({
  selector: 'dg-adventure-overview-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Button, AdventureForm],
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

    .seals,
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-2);
      margin: 0 0 var(--dg-space-3);
    }

    .seal {
      padding: 0.1rem 0.6rem;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--dg-vellum);
      background: var(--dg-ink-muted);
      border-radius: 999px;
    }

    .facts {
      margin: 0 0 var(--dg-space-3);
    }

    .description {
      white-space: pre-line;
    }

    .hint {
      margin: 0;
      font-style: italic;
    }
  `,
  template: `
    @let a = adventure();
    <h1>{{ a.name }}</h1>
    <div class="seals">
      <span class="seal">{{ statusLabels[a.status!] }}</span>
      <span class="seal">{{ visibilityLabels[a.visibility!] }}</span>
      @if (a.masterVacant) {
        <span class="seal">MJ absent</span>
      }
    </div>
    <p class="description">{{ a.description }}</p>
    <dl class="facts">
      <dt>MJ</dt>
      <dd>
        @if (a.masterVacant) {
          Aucun MJ pour le moment.
        } @else {
          <a [routerLink]="['/players', a.creatorId]">{{ masterName() }}</a>
        }
      </dd>
      <dt>Joueurs</dt>
      <dd>{{ a.players?.length ?? 0 }} / {{ a.maxPlayer }}</dd>
      <dt>Lieu</dt>
      <dd>{{ a.postalCode }} {{ a.city }}</dd>
    </dl>

    @if (canJoin()) {
      <dg-button [loading]="busy()" (click)="join()">Rejoindre</dg-button>
    }
    @if (context.isPlayer()) {
      <dg-button variant="danger" [loading]="busy()" (click)="quit()">
        Quitter l’aventure
      </dg-button>
    }

    @if (context.isMaster()) {
      <h2>Statut</h2>
      @if (transitions().length) {
        <div class="actions">
          @for (status of transitions(); track status) {
            <dg-button
              [variant]="status === 'ONGOING' ? 'primary' : 'ghost'"
              [disabled]="busy()"
              (click)="changeStatus(status)"
            >
              {{ statusActions[status] }}
            </dg-button>
          }
        </div>
      } @else {
        <p class="hint">Cette aventure est close : son statut ne changera plus.</p>
      }

      <h2>Visibilité</h2>
      <div class="actions">
        <dg-button variant="ghost" [disabled]="busy()" (click)="toggleVisibility()">
          {{ a.visibility === 'PUBLIC' ? 'Rendre privée' : 'Rendre publique' }}
        </dg-button>
      </div>

      @if (!closed()) {
        <h2>Modifier</h2>
        <dg-adventure-form
          submitLabel="Enregistrer"
          [initial]="formInitial()"
          [busy]="saving()"
          [withVisibility]="false"
          (saved)="save($event)"
        />
      }

      <h2>Supprimer</h2>
      <div class="actions">
        <dg-button
          variant="danger"
          [disabled]="a.status === 'ONGOING' || busy()"
          (click)="remove()"
        >
          Supprimer l’aventure
        </dg-button>
      </div>
      @if (a.status === 'ONGOING') {
        <p class="hint">
          Une aventure en cours ne peut pas être supprimée : termine-la ou abandonne-la d’abord.
        </p>
      }
    }
  `,
})
export class OverviewPage {
  protected readonly context = inject(AdventureContext);
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly directory = inject(AccountDirectory);
  private readonly confirmer = inject(ConfirmService);
  private readonly notifier = inject(NotifierService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly form = viewChild(AdventureForm);

  protected readonly statusLabels = STATUS_LABEL;
  protected readonly visibilityLabels = VISIBILITY_LABEL;
  protected readonly statusActions = STATUS_ACTION;

  protected readonly busy = signal(false);
  protected readonly saving = signal(false);

  // The shell only shows this page once the adventure is loaded.
  protected readonly adventure = computed(() => this.context.adventure()!);
  protected readonly closed = computed(() => isClosed(this.adventure().status as Status));
  protected readonly transitions = computed(() =>
    allowedTransitions(this.adventure().status as Status),
  );
  protected readonly masterName = computed(() =>
    this.directory.pseudo(this.adventure().creatorId!)(),
  );
  protected readonly canJoin = computed(() => {
    const a = this.adventure();
    return !this.context.isParticipant() && a.visibility === 'PUBLIC' && a.status === 'NOT_STARTED';
  });

  /**
   * What the form starts from. It is not the live adventure: a refresh must not wipe what the
   * master is typing, so it changes only on first display, after a save and on an explicit reload.
   */
  protected readonly formInitial = signal<AdventureValue>(toValue(this.context.adventure()!));

  protected save(value: AdventureValue): void {
    const id = this.adventure().id!;
    // The visibility has its own button and is never sent from here.
    const body: Partial<AdventureValue> = { ...value };
    delete body.visibility;
    this.saving.set(true);
    updateAdventure(this.http, this.rootUrl, { id, body })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.saving.set(false);
          this.context.update(response.body);
          this.formInitial.set(toValue(response.body));
          this.notifier.success('Aventure enregistrée.');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.onSaveFailed(toApiProblem(error));
        },
      });
  }

  private onSaveFailed(problem: ApiProblem): void {
    if (problem.status === 409) {
      // Keep the master's input until they choose to reload.
      this.notifier.conflict(() =>
        this.context.reload((fresh) => this.formInitial.set(toValue(fresh))),
      );
    } else if (!(problem.status === 400 && this.form()?.showProblem(problem))) {
      this.notifier.error(problem, 'Enregistrement de l’aventure');
    }
  }

  protected changeStatus(status: Status): void {
    const message = IRREVERSIBLE_MESSAGE[status];
    if (message === undefined) {
      this.applyStatus(status);
      return;
    }
    this.confirmer
      .confirm({ title: STATUS_ACTION[status], message, confirmLabel: STATUS_ACTION[status] })
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyStatus(status));
  }

  private applyStatus(status: Status): void {
    this.run(
      updateAdventureStatus(this.http, this.rootUrl, {
        id: this.adventure().id!,
        body: { status },
      }),
      'Changement de statut',
      (adventure) => this.context.update(adventure),
    );
  }

  protected toggleVisibility(): void {
    const visibility = this.adventure().visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';
    this.run(
      updateAdventureVisibility(this.http, this.rootUrl, {
        id: this.adventure().id!,
        body: { visibility },
      }),
      'Changement de visibilité',
      (adventure) => this.context.update(adventure),
    );
  }

  protected join(): void {
    this.run(
      joinAdventure(this.http, this.rootUrl, { id: this.adventure().id! }),
      'Rejoindre l’aventure',
      () => {
        this.notifier.success('Tu as rejoint l’aventure.');
        this.context.reload();
      },
    );
  }

  protected quit(): void {
    this.confirmer
      .confirm({
        title: 'Quitter l’aventure',
        message:
          'Tu ne feras plus partie de cette aventure. Tu pourras la rejoindre à nouveau si elle est publique.',
        confirmLabel: 'Quitter',
      })
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() =>
        this.run(
          quitAdventure(this.http, this.rootUrl, { id: this.adventure().id! }),
          'Quitter l’aventure',
          () => {
            this.notifier.success('Tu as quitté l’aventure.');
            void this.router.navigate(['/tavern/mine']);
          },
        ),
      );
  }

  protected remove(): void {
    const adventure = this.adventure();
    this.confirmer
      .typeToConfirm({
        title: 'Supprimer l’aventure',
        message: `Cette suppression est irréversible. Retape le nom de l’aventure (« ${adventure.name} ») pour confirmer.`,
        confirmLabel: 'Supprimer',
        inputLabel: 'Nom de l’aventure',
        inputType: 'text',
        expected: adventure.name,
      })
      .pipe(
        filter((typed): typed is string => typed !== null),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((confirmation) =>
        this.run(
          deleteAdventureById(this.http, this.rootUrl, {
            id: adventure.id!,
            body: { confirmation },
          }),
          'Suppression de l’aventure',
          () => {
            this.notifier.success('Aventure supprimée.');
            void this.router.navigate(['/tavern/mine']);
          },
        ),
      );
  }

  /** Runs one call with the buttons locked; a failure is shown and the adventure refreshed. */
  private run<T>(call: Observable<{ body: T }>, context: string, onDone: (body: T) => void): void {
    this.busy.set(true);
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.busy.set(false);
        onDone(response.body);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.notifier.error(toApiProblem(error), context);
        // A refusal usually means the adventure moved on: show it as it is now.
        this.context.reload();
      },
    });
  }
}
