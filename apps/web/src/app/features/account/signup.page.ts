import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { createAccount, resendVerificationEmail } from '../../core/api/generated/functions';
import { toApiProblem } from '../../core/errors/api-problem';
import { applyFieldErrors } from '../../core/errors/form-errors';
import { NotifierService } from '../../core/errors/notifier.service';
import { Button } from '../../shared/ui/button/button';
import { FormField } from '../../shared/ui/form-field/form-field';
import { AccountLayout } from './account-layout';
import { I18n } from '../../core/i18n/i18n.service';

const PSEUDO_PATTERN = /^\s*[\p{L}\p{N}_.-]{3,30}\s*$/u;
const PASSWORD_MAX_BYTES = 72;

/** The API hashes passwords with bcrypt, which reads 72 bytes at most (bytes, not characters). */
function maxBytes(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '');
  return new TextEncoder().encode(value).length <= PASSWORD_MAX_BYTES
    ? null
    : { maxbytes: { max: PASSWORD_MAX_BYTES } };
}

function sameAsPassword(control: AbstractControl): ValidationErrors | null {
  return control.value === control.parent?.get('password')?.value ? null : { mismatch: true };
}

@Component({
  selector: 'dg-signup-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AccountLayout, FormField, Button],
  template: `
    <dg-account-layout [heading]="t().account.signup.title">
      @if (sentTo(); as email) {
        <p>{{ t().account.signup.lastStep }}</p>
        @if (resent()) {
          <p role="status">
            {{ t().account.signup.resentTo(email) }}
          </p>
        }
        <div class="dg-account__actions">
          <dg-button variant="ghost" [loading]="resending()" (click)="resend(email)">
            {{ t().account.signup.resend }}
          </dg-button>
          <a routerLink="/login">{{ t().account.signup.signIn }}</a>
        </div>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <dg-form-field [label]="t().account.fields.pseudo" [control]="form.controls.pseudo">
            <input formControlName="pseudo" type="text" autocomplete="username" />
          </dg-form-field>
          <dg-form-field [label]="t().account.fields.email" [control]="form.controls.email">
            <input formControlName="email" type="email" autocomplete="email" />
          </dg-form-field>
          <dg-form-field [label]="t().account.fields.password" [control]="form.controls.password">
            <input formControlName="password" type="password" autocomplete="new-password" />
          </dg-form-field>
          <dg-form-field
            [label]="t().account.fields.confirmation"
            [control]="form.controls.confirmation"
          >
            <input formControlName="confirmation" type="password" autocomplete="new-password" />
          </dg-form-field>
          <div class="dg-account__actions">
            <dg-button type="submit" [loading]="busy()">{{ t().account.signup.submit }}</dg-button>
            <a routerLink="/login">{{ t().account.signup.toLogin }}</a>
          </div>
        </form>
      }
    </dg-account-layout>
  `,
})
export class SignupPage {
  protected readonly t = inject(I18n).t;

  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly busy = signal(false);
  protected readonly sentTo = signal<string | null>(null);
  protected readonly resending = signal(false);
  protected readonly resent = signal(false);

  protected readonly form = new FormGroup({
    pseudo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(PSEUDO_PATTERN)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(5),
        Validators.maxLength(50),
        Validators.email,
      ],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(8), maxBytes],
    }),
    confirmation: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, sameAsPassword],
    }),
  });

  constructor() {
    this.form.controls.password.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.confirmation.updateValueAndValidity());
  }

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    const { pseudo, email, password } = this.form.getRawValue();
    const body = { pseudo: pseudo.trim(), email: email.trim(), password };
    createAccount(this.http, this.rootUrl, { body })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.sentTo.set(body.email);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 409) {
            this.form.controls.pseudo.setErrors({ server: this.t().account.signup.pseudoTaken });
            this.form.controls.pseudo.markAsTouched();
          } else if (!(problem.status === 400 && applyFieldErrors(this.form, problem))) {
            this.notifier.error(problem, this.t().account.signup.context);
          }
        },
      });
  }

  protected resend(email: string): void {
    this.resending.set(true);
    resendVerificationEmail(this.http, this.rootUrl, { body: { email } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.resending.set(false);
          this.resent.set(true);
        },
        error: (error: unknown) => {
          this.resending.set(false);
          this.notifier.error(toApiProblem(error), this.t().account.signup.resending);
        },
      });
  }
}
