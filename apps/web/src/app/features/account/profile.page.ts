import { Location } from '@angular/common';
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
import { Router } from '@angular/router';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { deleteMyAccount, patchAccount } from '../../core/api/generated/functions';
import { AccountUpdateRequest } from '../../core/api/generated/models/account-update-request';
import { AuthService } from '../../core/auth/auth.service';
import { toApiProblem } from '../../core/errors/api-problem';
import { applyFieldErrors } from '../../core/errors/form-errors';
import { NotifierService } from '../../core/errors/notifier.service';
import { Button } from '../../shared/ui/button/button';
import { ConfirmService } from '../../shared/ui/confirm-dialog/confirm.service';
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

function sameAsNewPassword(control: AbstractControl): ValidationErrors | null {
  return control.value === control.parent?.get('newPassword')?.value ? null : { mismatch: true };
}

@Component({
  selector: 'dg-profile-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, AccountLayout, FormField, Button],
  styles: `
    // Two columns: identity then the danger zone on the left, password then sessions on the
    // right. On a narrow screen, one column in reading order (the danger zone last).
    .profile {
      display: grid;
      grid-template-columns: 1fr 1fr;
      align-items: start;
      gap: var(--dg-space-5);
    }

    .profile__column {
      display: flex;
      flex-direction: column;
      gap: var(--dg-space-5);
    }

    @media (max-width: 767.98px) {
      .profile {
        display: flex;
        flex-direction: column;
        align-items: stretch;
      }

      .profile__column {
        display: contents;
      }

      .profile__identity {
        order: 1;
      }

      .profile__password {
        order: 2;
      }

      .profile__sessions {
        order: 3;
      }

      .profile__danger {
        order: 4;
      }
    }

    section {
      padding: var(--dg-space-4) var(--dg-space-5);
      background: color-mix(in srgb, var(--dg-parchment-border) 8%, transparent);
      border: 1px solid color-mix(in srgb, var(--dg-parchment-border) 70%, transparent);
      border-radius: var(--dg-radius);
    }

    .profile__danger {
      border-color: color-mix(in srgb, var(--dg-blood) 55%, transparent);
      background: color-mix(in srgb, var(--dg-blood) 5%, transparent);
    }

    h2 {
      margin: 0 0 var(--dg-space-3);
      padding-bottom: var(--dg-space-2);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-md);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--dg-title-color);
      border-bottom: 1px solid color-mix(in srgb, var(--dg-parchment-border) 70%, transparent);
    }

    .pending {
      display: inline-block;
      margin: 0;
      padding: var(--dg-space-1) var(--dg-space-3);
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius-sm);
      font-size: var(--dg-text-sm);
    }

    .sessions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--dg-space-3);
    }
  `,
  template: `
    <dg-account-layout [heading]="t().account.profile.title" [wide]="true">
      <div class="profile">
        <div class="profile__column">
          <section class="profile__identity" aria-labelledby="profile-identity">
            <h2 id="profile-identity">{{ t().account.profile.identity }}</h2>
            <form id="identity-form" [formGroup]="identity" (ngSubmit)="saveIdentity()" novalidate>
              <dg-form-field
                [label]="t().account.fields.pseudo"
                [control]="identity.controls.pseudo"
              >
                <input formControlName="pseudo" type="text" autocomplete="username" />
              </dg-form-field>
              <dg-form-field [label]="t().account.fields.email" [control]="identity.controls.email">
                <input formControlName="email" type="email" autocomplete="email" />
              </dg-form-field>
              @if (pendingEmail(); as pending) {
                <p class="pending" role="status">{{ t().account.profile.pendingEmail(pending) }}</p>
              }
              @if (identityFailure(); as message) {
                <p class="dg-account__alert" role="alert">{{ message }}</p>
              }
              <div class="dg-account__actions">
                <dg-button type="submit" [loading]="savingIdentity()">{{
                  t().account.profile.save
                }}</dg-button>
              </div>
            </form>
          </section>

          <section class="profile__danger" aria-labelledby="profile-danger">
            <h2 id="profile-danger">{{ t().account.profile.danger }}</h2>
            <p>{{ t().account.profile.dangerText }}</p>
            @if (deleteFailure(); as message) {
              <p class="dg-account__alert" role="alert">{{ message }}</p>
            }
            <dg-button variant="danger" [loading]="deleting()" (click)="deleteAccount()">
              {{ t().account.profile.deleteAccount }}
            </dg-button>
          </section>
        </div>

        <div class="profile__column">
          <section class="profile__password" aria-labelledby="profile-password">
            <h2 id="profile-password">{{ t().account.profile.password }}</h2>
            <form id="password-form" [formGroup]="password" (ngSubmit)="savePassword()" novalidate>
              <dg-form-field
                [label]="t().account.profile.currentPassword"
                [control]="password.controls.currentPassword"
              >
                <input
                  formControlName="currentPassword"
                  type="password"
                  autocomplete="current-password"
                />
              </dg-form-field>
              <dg-form-field
                [label]="t().account.profile.newPassword"
                [control]="password.controls.newPassword"
              >
                <input formControlName="newPassword" type="password" autocomplete="new-password" />
              </dg-form-field>
              <dg-form-field
                [label]="t().account.profile.newConfirmation"
                [control]="password.controls.confirmation"
              >
                <input formControlName="confirmation" type="password" autocomplete="new-password" />
              </dg-form-field>
              <div class="dg-account__actions">
                <dg-button type="submit" [loading]="savingPassword()">
                  {{ t().account.profile.changePassword }}
                </dg-button>
              </div>
            </form>
          </section>

          <section class="profile__sessions" aria-labelledby="profile-sessions">
            <h2 id="profile-sessions">{{ t().account.profile.sessions }}</h2>
            <div class="sessions">
              <dg-button variant="ghost" (click)="signOut(false)">{{
                t().account.profile.signOut
              }}</dg-button>
              <dg-button variant="ghost" (click)="signOut(true)">
                {{ t().account.profile.signOutEverywhere }}
              </dg-button>
            </div>
          </section>
        </div>
      </div>

      <div class="dg-account__actions">
        <dg-button variant="ghost" (click)="back()">{{ t().account.profile.back }}</dg-button>
      </div>
    </dg-account-layout>
  `,
})
export class ProfilePage {
  protected readonly t = inject(I18n).t;

  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly notifier = inject(NotifierService);
  private readonly confirm = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly savingIdentity = signal(false);
  protected readonly savingPassword = signal(false);
  protected readonly deleting = signal(false);
  protected readonly deleteFailure = signal<string | null>(null);
  protected readonly identityFailure = signal<string | null>(null);
  protected readonly pendingEmail = signal<string | null>(
    this.auth.account()?.pendingEmail ?? null,
  );

  protected readonly identity = new FormGroup({
    pseudo: new FormControl(this.auth.account()?.pseudo ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(PSEUDO_PATTERN)],
    }),
    email: new FormControl(this.auth.account()?.email ?? '', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(5),
        Validators.maxLength(50),
        Validators.email,
      ],
    }),
  });

  protected readonly password = new FormGroup({
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(8), maxBytes],
    }),
    confirmation: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, sameAsNewPassword],
    }),
    currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    this.password.controls.newPassword.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.password.controls.confirmation.updateValueAndValidity());
  }

  protected saveIdentity(): void {
    if (this.savingIdentity()) {
      return;
    }
    if (this.identity.invalid) {
      this.identity.markAllAsTouched();
      return;
    }
    const { pseudo, email } = this.identity.getRawValue();
    const account = this.auth.account();
    const changes: Omit<AccountUpdateRequest, 'currentPassword'> = {};
    if (pseudo.trim() !== account?.pseudo) {
      changes.pseudo = pseudo.trim();
    }
    if (email.trim() !== account?.email) {
      changes.email = email.trim();
    }
    if (changes.pseudo === undefined && changes.email === undefined) {
      this.notifier.success(this.t().account.profile.nothingToChange);
      return;
    }
    this.identityFailure.set(null);
    // The API asks for the current password with any change; it is asked only once the reader
    // saves, so that the form keeps to what it changes.
    this.confirm
      .typeToConfirm({
        title: this.t().account.profile.confirmTitle,
        message: this.t().account.profile.confirmMessage,
        inputLabel: this.t().account.fields.password,
        inputType: 'password',
        confirmLabel: this.t().account.profile.save,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((currentPassword) => {
        if (currentPassword !== null) {
          this.sendIdentity({ ...changes, currentPassword });
        }
      });
  }

  private sendIdentity(body: AccountUpdateRequest): void {
    this.savingIdentity.set(true);
    this.update(body, this.identity, this.savingIdentity, () => {
      this.notifier.success(
        body.email === undefined
          ? this.t().account.profile.upToDate
          : this.t().account.profile.confirmationSent,
      );
    });
  }

  protected savePassword(): void {
    if (this.savingPassword()) {
      return;
    }
    if (this.password.invalid) {
      this.password.markAllAsTouched();
      return;
    }
    const { newPassword, currentPassword } = this.password.getRawValue();
    this.savingPassword.set(true);
    this.update({ currentPassword, newPassword }, this.password, this.savingPassword, () => {
      // The API ended every session, this one included.
      this.auth.forgetSession();
      this.notifier.success(this.t().account.profile.passwordChanged);
      void this.router.navigateByUrl('/login');
    });
  }

  private update(
    body: AccountUpdateRequest,
    form: FormGroup,
    busy: { set(value: boolean): void },
    done: () => void,
  ): void {
    patchAccount(this.http, this.rootUrl, { body })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          busy.set(false);
          this.pendingEmail.set(response.body.pendingEmail ?? null);
          if (body.newPassword === undefined) {
            this.auth.reloadAccount().subscribe({ error: () => undefined });
          }
          done();
        },
        error: (error: unknown) => {
          busy.set(false);
          this.showFailure(toApiProblem(error), body, form);
        },
      });
  }

  private showFailure(
    problem: ReturnType<typeof toApiProblem>,
    body: AccountUpdateRequest,
    form: FormGroup,
  ): void {
    if (problem.status === 403 && form === this.identity) {
      // The password was typed in the dialog, which is closed: the section says it.
      this.identityFailure.set(this.t().account.profile.wrongPassword);
    } else if (problem.status === 403) {
      this.fail(form, 'currentPassword', this.t().account.profile.wrongPassword);
    } else if (problem.status === 409 && body.email !== undefined) {
      this.fail(form, 'email', this.t().account.profile.emailTaken);
    } else if (problem.status === 409) {
      this.fail(form, 'pseudo', this.t().account.profile.pseudoTaken);
    } else if (!(problem.status === 400 && applyFieldErrors(form, problem))) {
      this.notifier.error(problem, this.t().account.profile.updating);
    }
  }

  private fail(form: FormGroup, field: string, message: string): void {
    const control = form.get(field)!;
    control.setErrors({ server: message });
    control.markAsTouched();
  }

  /** Back to the page the reader came from; home when they opened this one directly. */
  protected back(): void {
    if (this.router.lastSuccessfulNavigation()?.previousNavigation) {
      this.location.back();
    } else {
      void this.router.navigateByUrl('/');
    }
  }

  protected signOut(everywhere: boolean): void {
    (everywhere ? this.auth.logoutAll() : this.auth.logout())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.router.navigateByUrl('/'));
  }

  protected deleteAccount(): void {
    if (this.deleting()) {
      return;
    }
    this.deleteFailure.set(null);
    this.confirm
      .typeToConfirm({
        title: this.t().account.profile.deleteTitle,
        message: this.t().account.profile.deleteMessage,
        inputLabel: this.t().account.fields.password,
        inputType: 'password',
        confirmLabel: this.t().account.profile.deleteAccount,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmation) => {
        if (confirmation !== null) {
          this.performDelete(confirmation);
        }
      });
  }

  private performDelete(confirmation: string): void {
    this.deleting.set(true);
    deleteMyAccount(this.http, this.rootUrl, { body: { confirmation } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.deleting.set(false);
          this.auth.forgetSession();
          this.notifier.success(this.t().account.profile.deleted);
          void this.router.navigateByUrl('/');
        },
        error: (error: unknown) => {
          this.deleting.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 403) {
            this.deleteFailure.set(this.t().account.profile.wrongPassword);
          } else {
            this.notifier.error(problem, this.t().account.profile.deleting);
          }
        },
      });
  }
}
