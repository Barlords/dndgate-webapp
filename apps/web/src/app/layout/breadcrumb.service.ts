import { computed, inject, Injectable, signal } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

export interface Crumb {
  label: string;
  link: string;
}

/**
 * The trail of the current page, built from `data.breadcrumb` (a string) of the routes from the
 * root to the leaf. A page that loads its own name calls `setLast` once it has it.
 */
@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  private readonly router = inject(Router);

  private readonly trail = signal<Crumb[]>([]);
  private readonly lastLabel = signal<string | null>(null);

  readonly crumbs = computed<Crumb[]>(() => {
    const trail = this.trail();
    const last = this.lastLabel();
    if (last === null || trail.length === 0) {
      return trail;
    }
    return [...trail.slice(0, -1), { ...trail[trail.length - 1], label: last }];
  });

  constructor() {
    // The service may be created after the navigation it should describe has ended.
    this.trail.set(this.build(this.router.routerState.snapshot.root));
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      this.lastLabel.set(null);
      this.trail.set(this.build(this.router.routerState.snapshot.root));
    });
  }

  setLast(label: string): void {
    this.lastLabel.set(label);
  }

  private build(root: ActivatedRouteSnapshot): Crumb[] {
    const crumbs: Crumb[] = [];
    let url = '';
    for (
      let route: ActivatedRouteSnapshot | null = root;
      route !== null;
      route = route.firstChild
    ) {
      url += route.url.map((segment) => `/${segment.path}`).join('');
      // Read the route's own config: `snapshot.data` also holds what an empty-path parent gives.
      const label = route.routeConfig?.data?.['breadcrumb'];
      if (typeof label === 'string') {
        crumbs.push({ label, link: url || '/' });
      }
    }
    return crumbs;
  }
}
