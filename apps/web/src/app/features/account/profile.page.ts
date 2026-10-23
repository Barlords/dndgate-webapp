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
import { Router, RouterLink } from '@angular/router';
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

const PSEUDO_PATTERN = /^\s*[\p{L}\p{N}_.-]{3,30}\s*$/u;
const PASSWORD_MAX_BYTES = 72;
const WRONG_PASSWORD = 'Mot de passe incorrect.';

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
  imports: [ReactiveFormsModule, RouterLink, AccountLayout, FormField, Button],
  styles: `
    section {
      margin-bottom: var(--dg-space-5);
    }

    h2 {
      margin: 0 0 var(--dg-space-3);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-md);
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--dg-title-color);
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
    <dg-account-layout heading="Mon compte">
      <section aria-labelledby="profile-identity">
        <h2 id="profile-identity">Identité</h2>
        <form id="identity-form" [formGroup]="identity" (ngSubmit)="saveIdentity()" novalidate>
          <dg-form-field label="Pseudo" [control]="identity.controls.pseudo">
            <input formControlName="pseudo" type="text" autocomplete="username" />
          </dg-form-field>
          <dg-form-field label="Adresse e-mail" [control]="identity.controls.email">
            <input formControlName="email" type="email" autocomplete="email" />
          </dg-form-field>
          @if (pendingEmail(); as pending) {
            <p class="pending" role="status">en attente de confirmation : {{ pending }}</p>
          }
          <dg-form-field label="Mot de passe actuel" [control]="identity.controls.currentPassword">
            <input
              formControlName="currentPassword"
              type="password"
              autocomplete="current-password"
            />
          </dg-form-field>
          <div class="dg-account__actions">
            <dg-button type="submit" [loading]="savingIdentity()">Enregistrer</dg-button>
          </div>
        </form>
      </section>

      <section aria-labelledby="profile-password">
        <h2 id="profile-password">Mot de passe</h2>
        <form id="password-form" [formGroup]="password" (ngSubmit)="savePassword()" novalidate>
          <dg-form-field label="Nouveau mot de passe" [control]="password.controls.newPassword">
            <input formControlName="newPassword" type="password" autocomplete="new-password" />
          </dg-form-field>
          <dg-form-field
            label="Confirmation du nouveau mot de passe"
            [control]="password.controls.confirmation"
          >
            <input formControlName="confirmation" type="password" autocomplete="new-password" />
          </dg-form-field>
          <dg-form-field label="Mot de passe actuel" [control]="password.controls.currentPassword">
            <input
              formControlName="currentPassword"
              type="password"
              autocomplete="current-password"
            />
          </dg-form-field>
          <div class="dg-account__actions">
            <dg-button type="submit" [loading]="savingPassword()">
              Changer le mot de passe
            </dg-button>
          </div>
        </form>
      </section>

      <section aria-labelledby="profile-sessions">
        <h2 id="profile-sessions">Sessions</h2>
        <div class="sessions">
          <dg-button variant="ghost" (click)="signOut(false)">Se déconnecter</dg-button>
          <dg-button variant="ghost" (click)="signOut(true)">Se déconnecter de partout</dg-button>
        </div>
      </section>

      <section aria-labelledby="profile-danger">
        <h2 id="profile-danger">Zone dangereuse</h2>
        <p>
          Supprimer ton compte l'anonymise : ton pseudo, ton adresse et tes grimoires disparaissent,
          tes personnages et tes messages restent sous un nom effacé. C'est irréversible.
        </p>
        @if (deleteFailure(); as message) {
          <p class="dg-account__alert" role="alert">{{ message }}</p>
        }
        <dg-button variant="danger" [loading]="deleting()" (click)="deleteAccount()">
          Supprimer mon compte
        </dg-button>
      </section>

      <div class="dg-account__actions">
        <a routerLink="/tavern">Retour à la taverne</a>
      </div>
    </dg-account-layout>
  `,
})
export class ProfilePage {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifier = inject(NotifierService);
  private readonly confirm = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly savingIdentity = signal(false);
  protected readonly savingPassword = signal(false);
  protected readonly deleting = signal(false);
  protected readonly deleteFailure = signal<string | null>(null);
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
    currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
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
    const { pseudo, email, currentPassword } = this.identity.getRawValue();
    const account = this.auth.account();
    const body: AccountUpdateRequest = { currentPassword };
    if (pseudo.trim() !== account?.pseudo) {
      body.pseudo = pseudo.trim();
    }
    if (email.trim() !== account?.email) {
      body.email = email.trim();
    }
    if (body.pseudo === undefined && body.email === undefined) {
      this.notifier.success('Rien à modifier.');
      return;
    }
    this.savingIdentity.set(true);
    this.update(body, this.identity, this.savingIdentity, () => {
      this.identity.controls.currentPassword.reset('');
      this.notifier.success(
        body.email === undefined
          ? 'Ton profil est à jour.'
          : 'Un e-mail de confirmation a été envoyé à ta nouvelle adresse.',
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
      this.notifier.success('Mot de passe modifié. Reconnecte-toi avec le nouveau.');
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
    if (problem.status === 403) {
      this.fail(form, 'currentPassword', WRONG_PASSWORD);
    } else if (problem.status === 409 && body.email !== undefined) {
      this.fail(form, 'email', 'Cette adresse est déjà utilisée.');
    } else if (problem.status === 409) {
      this.fail(form, 'pseudo', 'Ce pseudo est déjà pris.');
    } else if (!(problem.status === 400 && applyFieldErrors(form, problem))) {
      this.notifier.error(problem, 'Modification du compte');
    }
  }

  private fail(form: FormGroup, field: string, message: string): void {
    const control = form.get(field)!;
    control.setErrors({ server: message });
    control.markAsTouched();
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
        title: 'Supprimer ton compte',
        message: 'Cette action est irréversible. Saisis ton mot de passe pour confirmer.',
        inputLabel: 'Mot de passe',
        inputType: 'password',
        confirmLabel: 'Supprimer mon compte',
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
          this.notifier.success('Ton compte a été supprimé.');
          void this.router.navigateByUrl('/');
        },
        error: (error: unknown) => {
          this.deleting.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 403) {
            this.deleteFailure.set(WRONG_PASSWORD);
          } else {
            this.notifier.error(problem, 'Suppression du compte');
          }
        },
      });
  }
}
