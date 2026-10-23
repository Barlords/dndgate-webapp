import { Component, inject, InjectionToken } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, ActivationEnd, provideRouter, Router } from '@angular/router';
import { BreadcrumbService } from './breadcrumb.service';

const NAMES = new InjectionToken<Record<string, string>>('NAMES');

@Component({ template: '' })
class Stub {}

describe('BreadcrumbService', () => {
  let service: BreadcrumbService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'library',
            data: { breadcrumb: 'Bibliothèque' },
            children: [
              { path: '', pathMatch: 'full', component: Stub },
              {
                path: 'spells',
                data: { breadcrumb: 'Sorts' },
                children: [
                  { path: '', pathMatch: 'full', component: Stub },
                  { path: ':index', component: Stub, data: { breadcrumb: 'Sort' } },
                ],
              },
            ],
          },
          { path: 'free', component: Stub },
          {
            path: 'named/:id',
            component: Stub,
            data: {
              breadcrumb: (route: ActivatedRouteSnapshot) => inject(NAMES)[route.params['id']],
            },
          },
        ]),
        { provide: NAMES, useValue: { a1: 'Acid Arrow' } },
      ],
    });
    service = TestBed.inject(BreadcrumbService);
    router = TestBed.inject(Router);
  });

  it('is empty before any navigation', () => {
    expect(service.crumbs()).toEqual([]);
  });

  it('builds crumbs from route data', async () => {
    await router.navigateByUrl('/library/spells/acid-arrow');
    expect(service.crumbs()).toEqual([
      { label: 'Bibliothèque', link: '/library' },
      { label: 'Sorts', link: '/library/spells' },
      { label: 'Sort', link: '/library/spells/acid-arrow' },
    ]);
  });

  it('does not repeat a crumb for an empty-path child', async () => {
    await router.navigateByUrl('/library');
    expect(service.crumbs()).toEqual([{ label: 'Bibliothèque', link: '/library' }]);
  });

  it('has no crumb for a route without a label', async () => {
    await router.navigateByUrl('/free');
    expect(service.crumbs()).toEqual([]);
  });

  it('setLast replaces the last label', async () => {
    await router.navigateByUrl('/library/spells/acid-arrow');
    service.setLast('Acid Arrow');
    expect(service.crumbs().map((c) => c.label)).toEqual(['Bibliothèque', 'Sorts', 'Acid Arrow']);
  });

  it('forgets the replaced label on the next navigation', async () => {
    await router.navigateByUrl('/library/spells/acid-arrow');
    service.setLast('Acid Arrow');
    await router.navigateByUrl('/library/spells');
    expect(service.crumbs().map((c) => c.label)).toEqual(['Bibliothèque', 'Sorts']);
  });

  it('setLast during activation survives NavigationEnd', async () => {
    const stop = router.events.subscribe((event) => {
      if (event instanceof ActivationEnd && event.snapshot.routeConfig?.path === ':index') {
        service.setLast('Acid Arrow');
      }
    });
    await router.navigateByUrl('/library/spells/acid-arrow');
    stop.unsubscribe();
    expect(service.crumbs().map((c) => c.label)).toEqual(['Bibliothèque', 'Sorts', 'Acid Arrow']);
  });

  it('setLast for a previous URL is ignored', async () => {
    await router.navigateByUrl('/library/spells/acid-arrow');
    service.setLast('Acid Arrow');
    await router.navigateByUrl('/library/spells/aid');
    expect(service.crumbs().map((c) => c.label)).toEqual(['Bibliothèque', 'Sorts', 'Sort']);
  });

  it('computes a label from the route, in an injection context', async () => {
    await router.navigateByUrl('/named/a1');
    expect(service.crumbs()).toEqual([{ label: 'Acid Arrow', link: '/named/a1' }]);
  });

  it('has no crumb when the computed label is undefined', async () => {
    await router.navigateByUrl('/named/zz');
    expect(service.crumbs()).toEqual([]);
  });

  it('ignores setLast when there are no crumbs', () => {
    service.setLast('Nothing');
    expect(service.crumbs()).toEqual([]);
  });
});
