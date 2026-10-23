import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { toApiProblem } from '../../core/errors/api-problem';
import { NotifierService } from '../../core/errors/notifier.service';
import { Button } from '../../shared/ui/button/button';
import { FormField } from '../../shared/ui/form-field/form-field';
import { AccountLayout } from './account-layout';

/** Only a path of this site is a safe place to go back to; `//host` would leave it. */
export function safeReturnUrl(value: string | null): string | null {
  return value !== null && value.startsWith('/') && !value.startsWith('//') ? value : null;
}

@Component({
  selector: 'dg-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AccountLayout, FormField, Button],
  template: `
    <dg-account-layout heading="Connexion">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <dg-form-field label="Pseudo" [control]="form.controls.pseudo">
          <input formControlName="pseudo" type="text" autocomplete="username" />
        </dg-form-field>
        <dg-form-field label="Mot de passe" [control]="form.controls.password">
          <input formControlName="password" type="password" autocomplete="current-password" />
        </dg-form-field>
        @if (failure(); as message) {
          <p class="dg-account__alert" role="alert">{{ message }}</p>
        }
        <div class="dg-account__actions">
          <dg-button type="submit" [loading]="busy()">Se connecter</dg-button>
          <a routerLink="/signup">Pas encore de compte ? Inscris-toi</a>
        </div>
      </form>
    </dg-account-layout>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly form = new FormGroup({
    pseudo: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const { pseudo, password } = this.form.getRawValue();
    this.auth
      .login(pseudo.trim(), password)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          const target = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
          void this.router.navigateByUrl(target ?? '/tavern');
        },
        error: (error: unknown) => {
          this.busy.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 401) {
            this.failure.set('Pseudo ou mot de passe incorrect.');
          } else {
            this.notifier.error(problem, 'Connexion');
          }
        },
      });
  }
}
