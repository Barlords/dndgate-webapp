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
  private readonly currentUrl = signal(this.router.url);
  // The label set by `setLast`, valid for the URL that set it only.
  private readonly override = signal<{ url: string; label: string } | null>(null);

  readonly crumbs = computed<Crumb[]>(() => {
    const trail = this.trail();
    const override = this.override();
    if (override === null || override.url !== this.currentUrl() || trail.length === 0) {
      return trail;
    }
    return [...trail.slice(0, -1), { ...trail[trail.length - 1], label: override.label }];
  });

  constructor() {
    // The service may be created after the navigation it should describe has ended.
    this.trail.set(this.build(this.router.routerState.snapshot.root));
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      this.currentUrl.set(this.router.url);
      this.trail.set(this.build(this.router.routerState.snapshot.root));
    });
  }

  /** Names the last crumb for the current URL; a page may call it while it is still activating. */
  setLast(label: string): void {
    this.override.set({ url: this.router.url, label });
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
