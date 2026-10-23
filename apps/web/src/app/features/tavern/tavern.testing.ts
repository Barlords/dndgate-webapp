import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { provideApi } from '../../core/api/api.providers';
import { AdventureDto } from '../../core/api/generated/models/adventure-dto';
import { AuthService } from '../../core/auth/auth.service';
import { TAVERN_ROUTES } from './tavern.routes';

/** For tests only: the Tavern routed under `/tavern` as in the app, with a backend double. */
export interface TavernTestbed {
  harness: RouterTestingHarness;
  router: Router;
  root: HTMLElement;
  backend: HttpTestingController;
  go(url: string): Promise<void>;
  render(): Promise<void>;
  /** The page content, without the sidebar. */
  main(): HTMLElement;
  /** The button of the page whose text is `label`. */
  button(label: string): HTMLButtonElement;
}

export function adventure(overrides: Partial<AdventureDto> = {}): AdventureDto {
  return {
    id: 'a1',
    creatorId: 'master',
    name: 'La mine perdue',
    description: 'Une mine.',
    maxPlayer: 4,
    players: ['p1'],
    status: 'NOT_STARTED',
    visibility: 'PUBLIC',
    postalCode: '75001',
    city: 'Paris',
    masterVacant: false,
    ...overrides,
  };
}

export function page(content: AdventureDto[], totalPages = 1, number = 0) {
  return { content, page: number, size: 100, totalElements: content.length, totalPages };
}

export async function setupTavern(): Promise<TavernTestbed> {
  localStorage.clear();
  const account = signal({ id: 'me', pseudo: 'Me' });
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideApi(),
      provideRouter([{ path: 'tavern', data: { breadcrumb: 'Taverne' }, children: TAVERN_ROUTES }]),
      { provide: AuthService, useValue: { account, isAuthenticated: () => true } },
      {
        provide: BreakpointObserver,
        useValue: { observe: () => of({ matches: false, breakpoints: {} }) },
      },
    ],
  });
  const harness = await RouterTestingHarness.create();
  const render = async () => {
    harness.detectChanges();
    await harness.fixture.whenStable();
  };
  const root = harness.fixture.nativeElement as HTMLElement;
  const main = () => root.querySelector('main')!;
  return {
    harness,
    router: TestBed.inject(Router),
    root,
    backend: TestBed.inject(HttpTestingController),
    render,
    main,
    button: (label) =>
      [...main().querySelectorAll('button')].find((b) => b.textContent?.trim() === label)!,
    go: async (url: string) => {
      await harness.navigateByUrl(url);
      await render();
    },
  };
}
