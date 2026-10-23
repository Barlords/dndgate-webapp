import { Routes } from '@angular/router';
import { TavernShell } from './tavern-shell';

/** Every Tavern page, under `/tavern`. */
export const TAVERN_ROUTES: Routes = [
  {
    path: '',
    component: TavernShell,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'quests' },
      {
        path: 'quests',
        data: { breadcrumb: 'Tableau des quêtes' },
        loadComponent: () => import('./quest-board.page').then((m) => m.QuestBoardPage),
      },
      {
        path: 'join',
        data: { breadcrumb: 'Rejoindre avec un code' },
        loadComponent: () => import('./join-by-code.page').then((m) => m.JoinByCodePage),
      },
      {
        path: 'new',
        data: { breadcrumb: 'Créer une aventure' },
        loadComponent: () => import('./create-adventure.page').then((m) => m.CreateAdventurePage),
      },
      {
        path: 'mine',
        data: { breadcrumb: 'Mes aventures' },
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'ongoing' },
          {
            path: 'ongoing',
            data: { breadcrumb: 'En cours', closed: false },
            loadComponent: () => import('./my-adventures.page').then((m) => m.MyAdventuresPage),
          },
          {
            path: 'finished',
            data: { breadcrumb: 'Terminées', closed: true },
            loadComponent: () => import('./my-adventures.page').then((m) => m.MyAdventuresPage),
          },
        ],
      },
      // Replaced by the adventure page.
      {
        path: 'adventures/:id',
        data: { breadcrumb: 'Aventure' },
        loadComponent: () => import('../not-found.page').then((m) => m.NotFoundPage),
      },
    ],
  },
  { path: '**', loadComponent: () => import('../not-found.page').then((m) => m.NotFoundPage) },
];
