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
import { allowedTransitions, isClosed, Status } from '../adventure-status';
import { AdventureContext } from './adventure-context.service';
import { I18n } from '../../../core/i18n/i18n.service';
import { AccountBadges } from '../../../shared/ui/account-badges/account-badges';

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
  imports: [RouterLink, Button, AdventureForm, AccountBadges],
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
      <span class="seal">{{ t().tavern.status[a.status!] }}</span>
      <span class="seal">{{ t().tavern.visibility[a.visibility!] }}</span>
      @if (a.masterVacant) {
        <span class="seal">{{ t().tavern.overview.masterAbsent }}</span>
      }
    </div>
    <p class="description">{{ a.description }}</p>
    <dl class="facts">
      <dt>{{ t().tavern.overview.master }}</dt>
      <dd>
        @if (a.masterVacant) {
          {{ t().tavern.overview.noMaster }}
        } @else {
          <a [routerLink]="['/players', a.creatorId]">{{ masterName() }}</a>
          <dg-account-badges [accountId]="adventure().creatorId" />
        }
      </dd>
      <dt>{{ t().tavern.overview.players }}</dt>
      <dd>{{ a.players?.length ?? 0 }} / {{ a.maxPlayer }}</dd>
      <dt>{{ t().tavern.overview.place }}</dt>
      <dd>{{ a.postalCode }} {{ a.city }}</dd>
    </dl>

    @if (canJoin()) {
      <dg-button [loading]="busy()" (click)="join()">{{ t().tavern.overview.join }}</dg-button>
    }
    @if (context.isPlayer()) {
      <dg-button variant="danger" [loading]="busy()" (click)="quit()">
        {{ t().tavern.overview.quit }}
      </dg-button>
    }

    @if (context.isMaster()) {
      <h2>{{ t().tavern.overview.status }}</h2>
      @if (transitions().length) {
        <div class="actions">
          @for (status of transitions(); track status) {
            <dg-button
              [variant]="status === 'ONGOING' ? 'primary' : 'ghost'"
              [disabled]="busy()"
              (click)="changeStatus(status)"
            >
              {{ t().tavern.overview.statusActions[status] }}
            </dg-button>
          }
        </div>
      } @else {
        <p class="hint">{{ t().tavern.overview.closed }}</p>
      }

      <h2>{{ t().tavern.overview.visibility }}</h2>
      <div class="actions">
        <dg-button variant="ghost" [disabled]="busy()" (click)="toggleVisibility()">
          {{
            a.visibility === 'PUBLIC'
              ? t().tavern.overview.makePrivate
              : t().tavern.overview.makePublic
          }}
        </dg-button>
      </div>

      @if (!closed()) {
        <h2>{{ t().tavern.overview.edit }}</h2>
        <dg-adventure-form
          [submitLabel]="t().common.actions.save"
          [initial]="formInitial()"
          [busy]="saving()"
          [withVisibility]="false"
          (saved)="save($event)"
        />
      }

      <h2>{{ t().tavern.overview.remove }}</h2>
      <div class="actions">
        <dg-button
          variant="danger"
          [disabled]="a.status === 'ONGOING' || busy()"
          (click)="remove()"
        >
          {{ t().tavern.overview.removeAdventure }}
        </dg-button>
      </div>
      @if (a.status === 'ONGOING') {
        <p class="hint">{{ t().tavern.overview.cannotRemove }}</p>
      }
    }
  `,
})
export class OverviewPage {
  protected readonly t = inject(I18n).t;

  protected readonly context = inject(AdventureContext);
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly directory = inject(AccountDirectory);
  private readonly confirmer = inject(ConfirmService);
  private readonly notifier = inject(NotifierService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly form = viewChild(AdventureForm);

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
          this.notifier.success(this.t().tavern.overview.saved);
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
      this.notifier.error(problem, this.t().tavern.overview.saving);
    }
  }

  protected changeStatus(status: Status): void {
    const texts = this.t().tavern.overview;
    const message = texts.irreversible[status];
    if (message === undefined) {
      this.applyStatus(status);
      return;
    }
    const action = texts.statusActions[status];
    this.confirmer
      .confirm({ title: action, message, confirmLabel: action })
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyStatus(status));
  }

  private applyStatus(status: Status): void {
    this.run(
      updateAdventureStatus(this.http, this.rootUrl, {
        id: this.adventure().id!,
        body: { status },
      }),
      this.t().tavern.overview.changingStatus,
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
      this.t().tavern.overview.changingVisibility,
      (adventure) => this.context.update(adventure),
    );
  }

  protected join(): void {
    this.run(
      joinAdventure(this.http, this.rootUrl, { id: this.adventure().id! }),
      this.t().tavern.overview.joining,
      () => {
        this.notifier.success(this.t().tavern.overview.joined);
        this.context.reload();
      },
    );
  }

  protected quit(): void {
    this.confirmer
      .confirm({
        title: this.t().tavern.overview.quitTitle,
        message: this.t().tavern.overview.quitMessage,
        confirmLabel: this.t().tavern.overview.quitConfirm,
      })
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() =>
        this.run(
          quitAdventure(this.http, this.rootUrl, { id: this.adventure().id! }),
          this.t().tavern.overview.quitting,
          () => {
            this.notifier.success(this.t().tavern.overview.quitted);
            void this.router.navigate(['/tavern/mine']);
          },
        ),
      );
  }

  protected remove(): void {
    const adventure = this.adventure();
    this.confirmer
      .typeToConfirm({
        title: this.t().tavern.overview.deleteTitle,
        message: this.t().tavern.overview.deleteMessage(adventure.name ?? ''),
        confirmLabel: this.t().common.actions.delete,
        inputLabel: this.t().tavern.overview.deleteInput,
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
          this.t().tavern.overview.deleting,
          () => {
            this.notifier.success(this.t().tavern.overview.deleted);
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
