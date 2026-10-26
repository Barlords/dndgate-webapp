import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { map, Observable, shareReplay, tap } from 'rxjs';
import { ApiConfiguration } from '../api/generated/api-configuration';
import { getPlans } from '../api/generated/functions';
import { PlanCatalogDto } from '../api/generated/models/plan-catalog-dto';
import { PlanDto } from '../api/generated/models/plan-dto';

/** The plans offered: fetched once per session, shared by every screen that names a plan. */
@Injectable({ providedIn: 'root' })
export class PlanCatalog {
  private readonly http = inject(HttpClient);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;

  private readonly loaded = signal<PlanCatalogDto | null>(null);
  readonly catalog = this.loaded.asReadonly();

  private request?: Observable<PlanCatalogDto>;

  load(): Observable<PlanCatalogDto> {
    this.request ??= getPlans(this.http, this.rootUrl).pipe(
      map((response) => response.body),
      tap((catalog) => this.loaded.set(catalog)),
      // shareReplay resets on error, so the next call after a failure fetches again.
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.request;
  }

  plan(id: string): PlanDto | undefined {
    return this.loaded()?.plans?.find((plan) => plan.id === id);
  }
}
