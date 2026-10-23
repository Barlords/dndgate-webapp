import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { resendVerificationEmail, verifyEmail } from '../../core/api/generated/functions';
import { toApiProblem } from '../../core/errors/api-problem';
import { NotifierService } from '../../core/errors/notifier.service';
import { Button } from '../../shared/ui/button/button';
import { FormField } from '../../shared/ui/form-field/form-field';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { AccountLayout } from './account-layout';

type State = 'checking' | 'done' | 'invalid' | 'taken' | 'failed';

@Component({
  selector: 'dg-verify-email-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AccountLayout, FormField, Button, Spinner],
  template: `
    <dg-account-layout heading="Validation du compte">
      @switch (state()) {
        @case ('checking') {
          <div class="dg-account__actions"><dg-spinner label="Validation en cours" /></div>
        }
        @case ('done') {
          <p role="status">Ton compte est validé.</p>
          <div class="dg-account__actions"><a routerLink="/login">Se connecter</a></div>
        }
        @case ('taken') {
          <p role="alert">Ce pseudo a été pris entre-temps : inscris-toi avec un autre.</p>
          <div class="dg-account__actions"><a routerLink="/signup">S'inscrire</a></div>
        }
        @case ('failed') {
          <p role="alert">La validation n'a pas pu aboutir pour le moment. Réessaie plus tard.</p>
        }
        @default {
          <p role="alert">Ce lien n'est plus valable.</p>
          @if (resent()) {
            <p role="status">
              Si une inscription est en attente pour cette adresse, un nouvel e-mail vient de
              partir.
            </p>
          }
          <form [formGroup]="resendForm" (ngSubmit)="resend()" novalidate>
            <dg-form-field label="Adresse e-mail" [control]="resendForm.controls.email">
              <input formControlName="email" type="email" autocomplete="email" />
            </dg-form-field>
            <div class="dg-account__actions">
              <dg-button type="submit" variant="ghost" [loading]="resending()">
                Renvoyer l'e-mail
              </dg-button>
            </div>
          </form>
        }
      }
    </dg-account-layout>
  `,
})
export class VerifyEmailPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly route = inject(ActivatedRoute);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<State>('checking');
  protected readonly resending = signal(false);
  protected readonly resent = signal(false);
  protected readonly resendForm = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
  });

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const email = params.get('email');
    const code = params.get('code');
    if (email) {
      this.resendForm.controls.email.setValue(email);
    }
    if (!email || !code) {
      this.state.set('invalid');
      return;
    }
    verifyEmail(this.http, this.rootUrl, { body: { email, code } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.state.set('done'),
        error: (error: unknown) => {
          const problem = toApiProblem(error);
          if (problem.status === 400) {
            this.state.set('invalid');
          } else if (problem.status === 409) {
            this.state.set('taken');
          } else {
            this.state.set('failed');
            this.notifier.error(problem, 'Validation du compte');
          }
        },
      });
  }

  protected resend(): void {
    if (this.resendForm.invalid) {
      this.resendForm.markAllAsTouched();
      return;
    }
    this.resending.set(true);
    const email = this.resendForm.controls.email.value.trim();
    resendVerificationEmail(this.http, this.rootUrl, { body: { email } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.resending.set(false);
          this.resent.set(true);
        },
        error: (error: unknown) => {
          this.resending.set(false);
          this.notifier.error(toApiProblem(error), 'Renvoi de l’e-mail');
        },
      });
  }
}
