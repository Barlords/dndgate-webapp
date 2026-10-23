import {
  computed,
  EnvironmentInjector,
  inject,
  Injectable,
  runInInjectionContext,
  signal,
} from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

/**
 * What a route puts in `data.breadcrumb`: a fixed label, or a function of the route (run in an
 * injection context) for a label that depends on its parameters; `undefined` means no crumb.
 */
export type CrumbLabel = string | ((route: ActivatedRouteSnapshot) => string | undefined);

export interface Crumb {
  label: string;
  link: string;
}

/**
 * The trail of the current page, built from `data.breadcrumb` (a `CrumbLabel`) of the routes from
 * the root to the leaf. A page that loads its own name calls `setLast` once it has it.
 */
@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  private readonly router = inject(Router);
  private readonly injector = inject(EnvironmentInjector);

  private readonly trail = signal<Crumb[]>([]);
  private readonly currentUrl = signal(this.router.url);
  // The label set by `setLast`, valid for the URL that set it only.
  private readonly override = signal<{ url: string; label: string } | null>(null);

  // Labels given to a crumb by its link, whatever the page shown below it.
  private readonly named = signal<Record<string, string>>({});

  readonly crumbs = computed<Crumb[]>(() => {
    const named = this.named();
    const trail = this.trail().map((crumb) =>
      crumb.link in named ? { ...crumb, label: named[crumb.link] } : crumb,
    );
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

  /** Names the crumb that links to `link`, which stays named while the reader moves below it. */
  setLabelAt(link: string, label: string): void {
    this.named.update((named) => ({ ...named, [link]: label }));
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
      const label = this.label(route, route.routeConfig?.data?.['breadcrumb']);
      if (label !== undefined) {
        crumbs.push({ label, link: url || '/' });
      }
    }
    return crumbs;
  }

  private label(route: ActivatedRouteSnapshot, label: unknown): string | undefined {
    if (typeof label === 'function') {
      return runInInjectionContext(this.injector, () =>
        (label as Exclude<CrumbLabel, string>)(route),
      );
    }
    return typeof label === 'string' ? label : undefined;
  }
}
