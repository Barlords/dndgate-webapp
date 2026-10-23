import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { login, refresh } from '../api/generated/functions';
import { AuthService } from './auth.service';
import { TokenStore } from './token-store';

const base = environment.apiBaseUrl;

/** Login and refresh carry no bearer and never trigger a refresh themselves. */
function isCredentialCall(url: string): boolean {
  return url.startsWith(base + login.PATH) || url.startsWith(base + refresh.PATH);
}

function withBearer(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token === null ? req : req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(base) || isCredentialCall(req.url)) {
    return next(req);
  }
  const auth = inject(AuthService);
  const tokens = inject(TokenStore);
  const router = inject(Router);

  const sentToken = tokens.accessToken();
  return next(withBearer(req, sentToken)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      const current = tokens.accessToken();
      // Another request already refreshed since this one left: reuse the newer token.
      if (current !== null && current !== sentToken) {
        return next(withBearer(req, current));
      }
      return auth.refreshAccessToken().pipe(
        catchError(() => {
          const hadSession = tokens.refreshToken() !== null || auth.isAuthenticated();
          auth.forgetSession();
          if (hadSession) {
            void router.navigateByUrl(`/login?returnUrl=${encodeURIComponent(router.url)}`);
          }
          return throwError(() => error);
        }),
        switchMap((token) => next(withBearer(req, token))),
      );
    }),
  );
};
