import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

// Pages not built yet resolve to the "lost" page; the tasks that create them swap the import.
const notBuilt = () => import('./features/not-found.page').then((m) => m.NotFoundPage);

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/home/home.page').then((m) => m.HomePage),
  },
  { path: 'login', loadComponent: notBuilt },
  { path: 'signup', loadComponent: notBuilt },
  { path: 'verify-email', loadComponent: notBuilt },
  { path: 'confirm-email', loadComponent: notBuilt },
  { path: 'account', canActivate: [authGuard], loadComponent: notBuilt },
  { path: 'players/:accountId', canActivate: [authGuard], loadComponent: notBuilt },
  {
    path: 'library',
    data: { breadcrumb: 'Bibliothèque' },
    loadChildren: () => import('./features/library/library.routes').then((m) => m.LIBRARY_ROUTES),
  },
  {
    path: 'tavern',
    canActivate: [authGuard],
    data: { breadcrumb: 'Taverne' },
    loadChildren: () => import('./features/tavern/tavern.routes').then((m) => m.TAVERN_ROUTES),
  },
  { path: 'adventures/:id', redirectTo: 'tavern/adventures/:id' },
  { path: '**', loadComponent: notBuilt },
];
