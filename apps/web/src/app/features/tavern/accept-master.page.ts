import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { acceptMasterInvitation } from '../../core/api/generated/functions';
import { toApiProblem } from '../../core/errors/api-problem';
import { applyFieldErrors } from '../../core/errors/form-errors';
import { NotifierService } from '../../core/errors/notifier.service';
import { Button } from '../../shared/ui/button/button';
import { FormField } from '../../shared/ui/form-field/form-field';
import { Parchment } from '../../shared/ui/parchment/parchment';
import { CODE_MAX } from './join-by-code.page';

const UNAVAILABLE = 'Ce code est invalide, expiré, ou l’aventure est terminée.';

/** « Devenir MJ avec un code »: any signed-in account can take the master role with a code. */
@Component({
  selector: 'dg-accept-master-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, ReactiveFormsModule, FormField, Button],
  styleUrl: './tavern-page.scss',
  template: `
    <dg-parchment>
      <h1>Devenir MJ avec un code</h1>
      <p class="lead">Un MJ t’a transmis un code pour reprendre son aventure ? Saisis-le ici.</p>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <dg-form-field label="Code d’invitation de MJ" [control]="form.controls.code">
          <input formControlName="code" type="text" autocomplete="off" />
        </dg-form-field>
        <dg-button type="submit" [loading]="busy()">Devenir MJ</dg-button>
      </form>
    </dg-parchment>
  `,
})
export class AcceptMasterPage {
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
    acceptMasterInvitation(this.http, this.rootUrl, { body: { code } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.busy.set(false);
          this.notifier.success('Tu es maintenant le MJ de cette aventure.');
          void this.router.navigate(['/tavern/adventures', body.id]);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409 || problem.status === 404) {
            this.form.controls.code.setErrors({ server: UNAVAILABLE });
            this.form.controls.code.markAsTouched();
          } else if (!(problem.status === 400 && applyFieldErrors(this.form, problem))) {
            this.notifier.error(problem, 'Invitation de MJ');
          }
        },
      });
  }
}
