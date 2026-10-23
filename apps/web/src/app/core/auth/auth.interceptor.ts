import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { login, refresh } from '../api/generated/functions';
import { AuthService } from './auth.service';
import { TokenStore } from './token-store';

const base = environment.apiBaseUrl;

/** Public routes (README, "Règles d'accès"): no bearer, and a 401 there never triggers a refresh. */
const PUBLIC_POST_PATHS = [
  '/accounts', // sign-up; PATCH and DELETE on this path are not public
  login.PATH,
  refresh.PATH,
  '/accounts/verify',
  '/accounts/email/confirm',
  '/accounts/verification-email',
].map((path) => base + path);

function isPublicCall(req: HttpRequest<unknown>): boolean {
  const path = req.url.split(/[?#]/)[0];
  return req.method === 'POST' && PUBLIC_POST_PATHS.includes(path);
}

function withBearer(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token === null ? req : req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(base) || isPublicCall(req)) {
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
