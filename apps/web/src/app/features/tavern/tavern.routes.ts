import { Routes } from '@angular/router';

// Placeholder until the tavern pages exist: every address shows the "lost" page.
export const TAVERN_ROUTES: Routes = [
  {
    path: '**',
    loadComponent: () => import('../not-found.page').then((m) => m.NotFoundPage),
  },
];
