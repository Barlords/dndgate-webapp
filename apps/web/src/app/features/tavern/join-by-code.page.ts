import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { acceptPlayerInvitation } from '../../core/api/generated/functions';
import { toApiProblem } from '../../core/errors/api-problem';
import { applyFieldErrors } from '../../core/errors/form-errors';
import { NotifierService } from '../../core/errors/notifier.service';
import { Button } from '../../shared/ui/button/button';
import { FormField } from '../../shared/ui/form-field/form-field';
import { Parchment } from '../../shared/ui/parchment/parchment';

export const CODE_MAX = 50;
const UNAVAILABLE = 'Cette aventure a déjà commencé ou est complète.';
const UNKNOWN_CODE = 'Ce code n’existe pas ou n’est plus valable.';

/** « Rejoindre avec un code »: the way into a private adventure. */
@Component({
  selector: 'dg-join-by-code-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, ReactiveFormsModule, FormField, Button],
  styleUrl: './tavern-page.scss',
  template: `
    <dg-parchment>
      <h1>Rejoindre avec un code</h1>
      <p class="lead">Le maître du jeu t’a envoyé un code d’invitation ? Saisis-le ici.</p>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <dg-form-field label="Code d’invitation" [control]="form.controls.code">
          <input formControlName="code" type="text" autocomplete="off" />
        </dg-form-field>
        <dg-button type="submit" [loading]="busy()">Rejoindre</dg-button>
      </form>
    </dg-parchment>
  `,
})
export class JoinByCodePage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly router = inject(Router);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly busy = signal(false);
  protected readonly form = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(CODE_MAX)],
    }),
  });

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    const code = this.form.controls.code.value.trim();
    if (this.form.invalid || code === '') {
      this.form.markAllAsTouched();
      if (code === '') {
        this.form.controls.code.setErrors({ required: true });
      }
      return;
    }
    this.busy.set(true);
    acceptPlayerInvitation(this.http, this.rootUrl, { body: { code } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.busy.set(false);
          void this.router.navigate(['/tavern/adventures', body.id]);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409 || problem.status === 404) {
            this.form.controls.code.setErrors({
              server: problem.status === 404 ? UNKNOWN_CODE : UNAVAILABLE,
            });
            this.form.controls.code.markAsTouched();
          } else if (!(problem.status === 400 && applyFieldErrors(this.form, problem))) {
            this.notifier.error(problem, 'Invitation');
          }
        },
      });
  }
}
