import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { crumb } from './layout/breadcrumb.service';

// Pages not built yet resolve to the "lost" page; the tasks that create them swap the import.
const notBuilt = () => import('./features/not-found.page').then((m) => m.NotFoundPage);

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/home/home.page').then((m) => m.HomePage),
  },
  {
    path: 'login',
    loadComponent: () => import('./features/account/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'signup',
    loadComponent: () => import('./features/account/signup.page').then((m) => m.SignupPage),
  },
  {
    path: 'verify-email',
    loadComponent: () =>
      import('./features/account/verify-email.page').then((m) => m.VerifyEmailPage),
  },
  {
    path: 'confirm-email',
    loadComponent: () =>
      import('./features/account/confirm-email.page').then((m) => m.ConfirmEmailPage),
  },
  {
    path: 'plans',
    loadComponent: () => import('./features/plans/plans.page').then((m) => m.PlansPage),
  },
  {
    path: 'account',
    canActivate: [authGuard],
    loadComponent: () => import('./features/account/profile.page').then((m) => m.ProfilePage),
  },
  {
    path: 'players/:accountId',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/account/public-profile.page').then((m) => m.PublicProfilePage),
  },
  {
    path: 'library',
    data: { breadcrumb: crumb((t) => t.library.shell.crumb) },
    loadChildren: () => import('./features/library/library.routes').then((m) => m.LIBRARY_ROUTES),
  },
  {
    path: 'tavern',
    canActivate: [authGuard],
    data: { breadcrumb: crumb((t) => t.tavern.shell.crumb) },
    loadChildren: () => import('./features/tavern/tavern.routes').then((m) => m.TAVERN_ROUTES),
  },
  { path: 'adventures/:id', redirectTo: 'tavern/adventures/:id' },
  { path: '**', loadComponent: notBuilt },
];
