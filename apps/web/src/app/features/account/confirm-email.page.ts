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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiConfiguration } from '../../core/api/generated/api-configuration';
import { confirmEmailChange } from '../../core/api/generated/functions';
import { AuthService } from '../../core/auth/auth.service';
import { toApiProblem } from '../../core/errors/api-problem';
import { NotifierService } from '../../core/errors/notifier.service';
import { Loading } from '../../shared/ui/loading/loading';
import { AccountLayout } from './account-layout';
import { I18n } from '../../core/i18n/i18n.service';

type State = 'checking' | 'done' | 'invalid' | 'taken' | 'failed';

@Component({
  selector: 'dg-confirm-email-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, AccountLayout, Loading],
  template: `
    <dg-account-layout [heading]="t().account.confirmEmail.title">
      @switch (state()) {
        @case ('checking') {
          <div class="dg-account__actions">
            <dg-loading
              [label]="t().account.confirmEmail.checking"
              [message]="t().account.confirmEmail.checkingFlavour"
            />
          </div>
        }
        @case ('done') {
          <p role="status">{{ t().account.confirmEmail.done }}</p>
        }
        @case ('taken') {
          <p role="alert">{{ t().account.confirmEmail.taken }}</p>
        }
        @case ('failed') {
          <p role="alert">{{ t().account.confirmEmail.failed }}</p>
        }
        @default {
          <p role="alert">{{ t().account.confirmEmail.expired }}</p>
        }
      }
      @if (state() !== 'checking') {
        <div class="dg-account__actions">
          @if (auth.isAuthenticated()) {
            <a routerLink="/account">{{ t().account.confirmEmail.backToAccount }}</a>
          } @else {
            <a routerLink="/login">{{ t().account.signup.signIn }}</a>
          }
        </div>
      }
    </dg-account-layout>
  `,
})
export class ConfirmEmailPage implements OnInit {
  protected readonly t = inject(I18n).t;

  protected readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;
  private readonly route = inject(ActivatedRoute);
  private readonly notifier = inject(NotifierService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<State>('checking');

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const email = params.get('email');
    const code = params.get('code');
    if (!email || !code) {
      this.state.set('invalid');
      return;
    }
    confirmEmailChange(this.http, this.rootUrl, { body: { email, code } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.state.set('done');
          if (this.auth.isAuthenticated()) {
            // Refresh the address shown on the account page (it is no longer "pending").
            this.auth
              .reloadAccount()
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe({ error: () => undefined });
          }
        },
        error: (error: unknown) => {
          const problem = toApiProblem(error);
          if (problem.status === 400) {
            this.state.set('invalid');
          } else if (problem.status === 409) {
            this.state.set('taken');
          } else {
            this.state.set('failed');
            this.notifier.error(problem, this.t().account.confirmEmail.context);
          }
        },
      });
  }
}
