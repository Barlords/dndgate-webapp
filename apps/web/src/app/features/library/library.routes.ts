import { Routes } from '@angular/router';

// Placeholder until the library pages exist: every address shows the "lost" page.
export const LIBRARY_ROUTES: Routes = [
  {
    path: '**',
    loadComponent: () => import('../not-found.page').then((m) => m.NotFoundPage),
  },
];
