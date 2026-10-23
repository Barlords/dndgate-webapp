import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BreadcrumbService } from './breadcrumb.service';

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
        ]),
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

  it('ignores setLast when there are no crumbs', () => {
    service.setLast('Nothing');
    expect(service.crumbs()).toEqual([]);
  });
});
