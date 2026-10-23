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
      {
        path: 'adventures/:id',
        data: { breadcrumb: 'Aventure' },
        loadComponent: () => import('./adventure/adventure-shell').then((m) => m.AdventureShell),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'overview' },
          {
            path: 'overview',
            data: { breadcrumb: 'Informations' },
            loadComponent: () => import('./adventure/overview.page').then((m) => m.OverviewPage),
          },
          {
            path: 'players',
            data: { breadcrumb: 'Joueurs' },
            loadComponent: () => import('./adventure/players.page').then((m) => m.PlayersPage),
          },
          {
            path: 'characters',
            data: { breadcrumb: 'Personnages' },
            children: [
              {
                path: '',
                pathMatch: 'full',
                loadComponent: () =>
                  import('./adventure/characters/character-list.page').then(
                    (m) => m.CharacterListPage,
                  ),
              },
              {
                path: 'new',
                data: { breadcrumb: 'Nouveau personnage' },
                loadComponent: () =>
                  import('./adventure/characters/character-form.page').then(
                    (m) => m.CharacterFormPage,
                  ),
              },
              {
                path: ':characterId',
                data: { breadcrumb: 'Personnage' },
                children: [
                  {
                    path: '',
                    pathMatch: 'full',
                    loadComponent: () =>
                      import('./adventure/characters/character-detail.page').then(
                        (m) => m.CharacterDetailPage,
                      ),
                  },
                  {
                    path: 'edit',
                    data: { breadcrumb: 'Modifier' },
                    loadComponent: () =>
                      import('./adventure/characters/character-form.page').then(
                        (m) => m.CharacterFormPage,
                      ),
                  },
                ],
              },
            ],
          },
          // Built by later tasks.
          {
            path: 'chronicle',
            data: { breadcrumb: 'Chronique' },
            loadComponent: () => import('./adventure/chronicle.page').then((m) => m.ChroniclePage),
          },
          {
            path: 'gm-notes',
            data: { breadcrumb: 'Notes du MJ' },
            loadComponent: () => import('./adventure/gm-notes.page').then((m) => m.GmNotesPage),
          },
          ...[
            ['planning', 'Planning'],
            ['messages', 'Messagerie'],
            ['master', 'MJ'],
          ].map(([path, breadcrumb]) => ({
            path,
            data: { breadcrumb },
            loadComponent: () =>
              import('./adventure/coming-soon.page').then((m) => m.ComingSoonPage),
          })),
        ],
      },
    ],
  },
  { path: '**', loadComponent: () => import('../not-found.page').then((m) => m.NotFoundPage) },
];
