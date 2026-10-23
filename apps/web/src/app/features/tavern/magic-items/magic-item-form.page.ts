import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { ApiConfiguration } from '../../../core/api/generated/api-configuration';
import {
  createMagicItem,
  deleteMagicItemById,
  getMagicItemById,
  patchMagicItem,
} from '../../../core/api/generated/functions';
import { MagicItemDto } from '../../../core/api/generated/models/magic-item-dto';
import { ApiProblem, toApiProblem } from '../../../core/errors/api-problem';
import { NotifierService } from '../../../core/errors/notifier.service';
import { BreadcrumbService } from '../../../layout/breadcrumb.service';
import { Button } from '../../../shared/ui/button/button';
import { ConfirmService } from '../../../shared/ui/confirm-dialog/confirm.service';
import { LoadError } from '../../../shared/ui/load-error/load-error';
import { Loading } from '../../../shared/ui/loading/loading';
import { Parchment } from '../../../shared/ui/parchment/parchment';
import { BLANK_MAGIC_ITEM, MagicItemForm, MagicItemValue } from './magic-item-form';

const LIMIT_REACHED: ApiProblem = {
  status: 409,
  detail: 'tu as atteint la limite de 500 objets magiques.',
  errors: [],
};

type PageState = 'loading' | 'ready' | 'missing' | 'failed';

function toValue(item: MagicItemDto): MagicItemValue {
  return {
    name: item.name ?? '',
    type: item.type ?? BLANK_MAGIC_ITEM.type,
    rarity: item.rarity ?? BLANK_MAGIC_ITEM.rarity,
    requiresAttunement: item.requiresAttunement ?? false,
    description: item.description ?? '',
  };
}

/** Creation of a magic item (`magic-items/new`), and edition or deletion of one (`magic-items/:id`). */
@Component({
  selector: 'dg-magic-item-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, MagicItemForm, Button, LoadError, Loading, RouterLink],
  styleUrl: '../tavern-page.scss',
  template: `
    <dg-parchment>
      <h1>{{ itemId === null ? 'Nouvel objet magique' : 'Modifier l’objet magique' }}</h1>
      @switch (state()) {
        @case ('ready') {
          <dg-magic-item-form
            [initial]="initial()"
            [submitLabel]="itemId === null ? 'Créer l’objet' : 'Enregistrer'"
            [busy]="saving()"
            (saved)="save($event)"
          >
            @if (itemId !== null) {
              <dg-button variant="danger" [loading]="deleting()" (click)="remove()">
                Supprimer
              </dg-button>
            }
          </dg-magic-item-form>
          <p><a routerLink="/tavern/magic-items">Retour à mes objets magiques</a></p>
        }
        @case ('missing') {
          <div class="state">
            <p role="alert">Cet objet magique est introuvable.</p>
            <a routerLink="/tavern/magic-items">Retour à mes objets magiques</a>
          </div>
        }
        @case ('failed') {
          <div class="state">
            <dg-load-error
              message="Impossible de charger l’objet magique."
              [problem]="loadProblem()"
              (retry)="load()"
            />
          </div>
        }
        @default {
          <div class="state">
            <dg-loading label="Chargement de l’objet magique" message="Le coffre s’entrouvre…" />
          </div>
        }
      }
    </dg-parchment>
  `,
})
export class MagicItemFormPage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly notifier = inject(NotifierService);
  private readonly confirmer = inject(ConfirmService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly form = viewChild(MagicItemForm);

  /** The edited item; null when creating. */
  protected readonly itemId = inject(ActivatedRoute).snapshot.paramMap.get('id');

  protected readonly state = signal<PageState>(this.itemId === null ? 'ready' : 'loading');
  protected readonly loadProblem = signal<ApiProblem | null>(null);
  protected readonly initial = signal<MagicItemValue>(BLANK_MAGIC_ITEM);
  private readonly item = signal<MagicItemDto | null>(null);
  protected readonly saving = signal(false);
  protected readonly deleting = signal(false);
  private request: Subscription | null = null;

  constructor() {
    this.load();
  }

  protected load(): void {
    if (this.itemId === null) {
      return;
    }
    this.state.set('loading');
    this.request?.unsubscribe();
    this.request = getMagicItemById(this.http, this.rootUrl, { id: this.itemId })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.item.set(body);
          this.initial.set(toValue(body));
          this.state.set('ready');
          this.breadcrumb.setLast(body.name ?? '');
        },
        error: (error: unknown) => {
          const problem = toApiProblem(error);
          this.loadProblem.set(problem);
          this.state.set(problem.status === 404 ? 'missing' : 'failed');
        },
      });
  }

  protected save(value: MagicItemValue): void {
    this.saving.set(true);
    if (this.itemId === null) {
      this.create(value);
    } else {
      this.update(this.itemId, value);
    }
  }

  private create(body: MagicItemValue): void {
    createMagicItem(this.http, this.rootUrl, { body })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.notifier.success('Objet magique créé.');
          void this.router.navigate(['/tavern/magic-items']);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.notifier.error(LIMIT_REACHED);
          } else {
            this.onRefused(problem, 'Création de l’objet magique');
          }
        },
      });
  }

  private update(id: string, body: MagicItemValue): void {
    patchMagicItem(this.http, this.rootUrl, { id, body })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.notifier.success('Objet magique enregistré.');
          void this.router.navigate(['/tavern/magic-items']);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            // Keep what is typed until the reader chooses to reload.
            this.notifier.conflict(() => this.load());
          } else {
            this.onRefused(problem, 'Enregistrement de l’objet magique');
          }
        },
      });
  }

  protected remove(): void {
    const item = this.item()!;
    this.confirmer
      .typeToConfirm({
        title: 'Supprimer l’objet magique',
        message: `Cette suppression est irréversible. Retape le nom de l’objet (« ${item.name} ») pour confirmer.`,
        confirmLabel: 'Supprimer',
        inputLabel: 'Nom de l’objet',
        inputType: 'text',
        expected: item.name,
      })
      .pipe(
        filter((typed): typed is string => typed !== null),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((confirmation) => {
        this.deleting.set(true);
        deleteMagicItemById(this.http, this.rootUrl, { id: item.id!, body: { confirmation } })
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.deleting.set(false);
              this.notifier.success('Objet magique supprimé.');
              void this.router.navigate(['/tavern/magic-items']);
            },
            error: (error: unknown) => {
              this.deleting.set(false);
              this.notifier.error(toApiProblem(error), 'Suppression de l’objet magique');
            },
          });
      });
  }

  private onRefused(problem: ApiProblem, context: string): void {
    if (!(problem.status === 400 && this.form()?.showProblem(problem))) {
      this.notifier.error(problem, context);
    }
  }
}
