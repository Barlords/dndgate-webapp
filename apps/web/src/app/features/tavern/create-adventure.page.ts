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
import { Router } from '@angular/router';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { createAdventure } from '../../core/api/generated/functions';
import { ApiProblem, toApiProblem } from '../../core/errors/api-problem';
import { NotifierService } from '../../core/errors/notifier.service';
import { Parchment } from '../../shared/ui/parchment/parchment';
import { AdventureForm, AdventureValue } from './adventure-form';

const LIMIT_REACHED: ApiProblem = {
  status: 409,
  detail: 'tu mènes déjà 50 aventures actives, termine ou abandonne-en une avant d’en créer.',
  errors: [],
};

/** « Créer une aventure »: the caller becomes its game master. */
@Component({
  selector: 'dg-create-adventure-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, AdventureForm],
  styleUrl: './tavern-page.scss',
  template: `
    <dg-parchment>
      <h1>Créer une aventure</h1>
      <dg-adventure-form
        [busy]="saving()"
        submitLabel="Créer l’aventure"
        (saved)="create($event)"
      />
    </dg-parchment>
  `,
})
export class CreateAdventurePage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly router = inject(Router);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly form = viewChild(AdventureForm);
  protected readonly saving = signal(false);

  protected create(body: AdventureValue): void {
    this.saving.set(true);
    createAdventure(this.http, this.rootUrl, { body })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body: created }) => {
          this.saving.set(false);
          this.notifier.success('Aventure créée.');
          void this.router.navigate(['/tavern/adventures', created.id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.notifier.error(LIMIT_REACHED);
          } else if (!(problem.status === 400 && this.form()?.showProblem(problem))) {
            this.notifier.error(problem, 'Création de l’aventure');
          }
        },
      });
  }
}
