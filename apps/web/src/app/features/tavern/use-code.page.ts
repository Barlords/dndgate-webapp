import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { acceptMasterInvitation, acceptPlayerInvitation } from '../../core/api/generated/functions';
import { ApiProblem, toApiProblem } from '../../core/errors/api-problem';
import { applyFieldErrors } from '../../core/errors/form-errors';
import { NotifierService } from '../../core/errors/notifier.service';
import { Button } from '../../shared/ui/button/button';
import { FormField } from '../../shared/ui/form-field/form-field';
import { Parchment } from '../../shared/ui/parchment/parchment';
import { I18n } from '../../core/i18n/i18n.service';

export const CODE_MAX = 50;

/**
 * « Utiliser un code »: one field for both kinds of invitation. The code is first tried as a
 * player's (the way into a private adventure); one the API does not know as such is then tried as
 * a game master's (taking over the adventure of another master).
 */
@Component({
  selector: 'dg-use-code-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Parchment, ReactiveFormsModule, FormField, Button],
  styleUrl: './tavern-page.scss',
  template: `
    <dg-parchment>
      <h1>{{ t().tavern.code.title }}</h1>
      <p class="lead">{{ t().tavern.code.lead }}</p>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <dg-form-field [label]="t().tavern.code.field" [control]="form.controls.code">
          <input formControlName="code" type="text" autocomplete="off" />
        </dg-form-field>
        <dg-button type="submit" [loading]="busy()">{{ t().tavern.code.submit }}</dg-button>
      </form>
    </dg-parchment>
  `,
})
export class UseCodePage {
  protected readonly t = inject(I18n).t;

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
        next: ({ body }) => this.open(body.id!),
        error: (error: unknown) => {
          const problem = toApiProblem(error);
          if (problem.status === 404) {
            this.tryAsMaster(code);
          } else if (problem.status === 409) {
            this.refuse(this.t().tavern.code.unavailable);
          } else {
            this.fail(problem, this.t().tavern.code.invitation);
          }
        },
      });
  }

  private tryAsMaster(code: string): void {
    acceptMasterInvitation(this.http, this.rootUrl, { body: { code } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ body }) => {
          this.notifier.success(this.t().tavern.code.nowMaster);
          this.open(body.id!);
        },
        error: (error: unknown) => {
          const problem = toApiProblem(error);
          if (problem.status === 404 || problem.status === 409) {
            this.refuse(this.t().tavern.code.unknown);
          } else {
            this.fail(problem, this.t().tavern.code.masterInvitation);
          }
        },
      });
  }

  private open(adventureId: string): void {
    this.busy.set(false);
    void this.router.navigate(['/tavern/adventures', adventureId]);
  }

  private refuse(message: string): void {
    this.busy.set(false);
    this.form.controls.code.setErrors({ server: message });
    this.form.controls.code.markAsTouched();
  }

  private fail(problem: ApiProblem, context: string): void {
    this.busy.set(false);
    if (!(problem.status === 400 && applyFieldErrors(this.form, problem))) {
      this.notifier.error(problem, context);
    }
  }
}
