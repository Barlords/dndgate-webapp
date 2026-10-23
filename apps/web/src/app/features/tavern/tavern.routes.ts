import { Routes } from '@angular/router';
import { TavernShell } from './tavern-shell';

/** Every Tavern page, under `/tavern`. */
export const TAVERN_ROUTES: Routes = [
  {
    path: '',
    component: TavernShell,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'adventures' },
      {
        path: 'adventures',
        pathMatch: 'full',
        data: { breadcrumb: 'Aventures' },
        loadComponent: () => import('./quest-board.page').then((m) => m.QuestBoardPage),
      },
      {
        path: 'code',
        data: { breadcrumb: 'Utiliser un code' },
        loadComponent: () => import('./use-code.page').then((m) => m.UseCodePage),
      },
      {
        path: 'new',
        data: { breadcrumb: 'Créer une aventure' },
        loadComponent: () => import('./create-adventure.page').then((m) => m.CreateAdventurePage),
      },
      {
        path: 'mine',
        data: { breadcrumb: 'Mes aventures' },
        loadComponent: () => import('./my-adventures.page').then((m) => m.MyAdventuresPage),
      },
      {
        path: 'characters',
        data: { breadcrumb: 'Mes personnages' },
        children: [
          {
            path: '',
            pathMatch: 'full',
            loadComponent: () =>
              import('./characters/my-characters.page').then((m) => m.MyCharactersPage),
          },
          {
            // Not an adventure's page: the frame draws the parchment around them.
            path: '',
            loadComponent: () =>
              import('./characters/character-frame').then((m) => m.CharacterFrame),
            children: [
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
        ],
      },
      {
        path: 'grimoires',
        data: { breadcrumb: 'Mes grimoires' },
        loadComponent: () =>
          import('../library/spellbooks/spellbook-list.page').then((m) => m.SpellBookListPage),
      },
      {
        path: 'magic-items',
        data: { breadcrumb: 'Mes objets magiques' },
        children: [
          {
            path: '',
            pathMatch: 'full',
            loadComponent: () =>
              import('./magic-items/my-magic-items.page').then((m) => m.MyMagicItemsPage),
          },
          {
            path: 'new',
            data: { breadcrumb: 'Nouvel objet magique' },
            loadComponent: () =>
              import('./magic-items/magic-item-form.page').then((m) => m.MagicItemFormPage),
          },
          {
            path: ':id',
            // Replaced by the item's name once it has loaded.
            data: { breadcrumb: 'Objet magique' },
            loadComponent: () =>
              import('./magic-items/magic-item-form.page').then((m) => m.MagicItemFormPage),
          },
        ],
      },
      // The former addresses of these pages, kept for bookmarks.
      { path: 'quests', redirectTo: 'adventures' },
      { path: 'join', redirectTo: 'code' },
      { path: 'master-code', redirectTo: 'code' },
      { path: 'mine/ongoing', redirectTo: 'mine' },
      { path: 'mine/finished', redirectTo: 'mine' },
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
          {
            path: 'planning',
            data: { breadcrumb: 'Planning' },
            loadComponent: () => import('./adventure/planning.page').then((m) => m.PlanningPage),
          },
          {
            path: 'messages',
            data: { breadcrumb: 'Messagerie' },
            loadComponent: () => import('./adventure/messages.page').then((m) => m.MessagesPage),
          },
          {
            path: 'master',
            data: { breadcrumb: 'MJ' },
            loadComponent: () => import('./adventure/master.page').then((m) => m.MasterPage),
          },
        ],
      },
    ],
  },
  { path: '**', loadComponent: () => import('../not-found.page').then((m) => m.NotFoundPage) },
];
