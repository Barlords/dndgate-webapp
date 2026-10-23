import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanMatchFn, Routes } from '@angular/router';
import { authGuard } from '../../core/auth/auth.guard';
import { CrumbLabel } from '../../layout/breadcrumb.service';
import { LibraryShell } from './library-shell';
import { RulesetRegistry } from './rulesets/ruleset-registry';
import { SelectedRulesetService } from './rulesets/selected-ruleset.service';

const notFound = () => import('../not-found.page').then((m) => m.NotFoundPage);

/** Matches `<ruleset>[/<section>…]` only for a registered ruleset and one of its sections. */
const knownRulesetAndSection: CanMatchFn = (_route, segments) => {
  const ruleset = inject(RulesetRegistry).get(segments[0]?.path ?? '');
  const sectionId = segments[1]?.path;
  return (
    ruleset !== undefined &&
    (sectionId === undefined || ruleset.sections.some((section) => section.id === sectionId))
  );
};

function param(route: ActivatedRouteSnapshot, name: string): string {
  return route.pathFromRoot.find((step) => step.paramMap.has(name))?.paramMap.get(name) ?? '';
}

const sectionCrumb: CrumbLabel = (route) =>
  inject(RulesetRegistry)
    .get(param(route, 'rulesetId'))
    ?.sections.find((section) => section.id === param(route, 'sectionId'))?.label;

// Until the entry has loaded and named itself, its crumb is its index.
const entryCrumb: CrumbLabel = (route) => param(route, 'index');

/** Every Library page, under `/library`; the pages only ever talk to the `Ruleset` abstraction. */
export const LIBRARY_ROUTES: Routes = [
  {
    path: '',
    component: LibraryShell,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: () => inject(SelectedRulesetService).current().id,
      },
      // Declared before the parameterised routes, which would otherwise read them as a ruleset.
      {
        path: 'grimoires',
        canActivate: [authGuard],
        data: { breadcrumb: 'Mes grimoires' },
        loadComponent: notFound,
      },
      { path: 'grimoires/:id', canActivate: [authGuard], loadComponent: notFound },
      {
        path: ':rulesetId',
        canMatch: [knownRulesetAndSection],
        children: [
          {
            path: '',
            pathMatch: 'full',
            loadComponent: () => import('./pages/ruleset-home.page').then((m) => m.RulesetHomePage),
          },
          {
            path: ':sectionId',
            data: { breadcrumb: sectionCrumb },
            children: [
              {
                path: '',
                pathMatch: 'full',
                loadComponent: () =>
                  import('./pages/section-list.page').then((m) => m.SectionListPage),
              },
              {
                path: ':index',
                data: { breadcrumb: entryCrumb },
                loadComponent: () =>
                  import('./pages/entry-detail.page').then((m) => m.EntryDetailPage),
              },
            ],
          },
        ],
      },
    ],
  },
  // An unknown ruleset or section, or a path too deep.
  { path: '**', loadComponent: notFound },
];
