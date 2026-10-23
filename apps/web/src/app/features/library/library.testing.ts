import { BreakpointObserver } from '@angular/cdk/layout';
import { signal, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { AccountDto } from '../../core/api/generated/models/account-dto';
import { AuthService } from '../../core/auth/auth.service';
import { LIBRARY_ROUTES } from './library.routes';
import { FakeRuleset } from './rulesets/fake.ruleset';
import { Ruleset, RULESETS } from './rulesets/ruleset.model';

/** For tests only: the Library routed under `/library` as in the app, with fake rulesets. */
export interface LibraryTestbed {
  harness: RouterTestingHarness;
  router: Router;
  root: HTMLElement;
  account: WritableSignal<AccountDto | null>;
  /** Navigates and lets the page render. */
  go(url: string): Promise<void>;
  /** Lets pending changes render. */
  render(): Promise<void>;
}

export async function setupLibrary(
  options: { rulesets?: Ruleset[]; signedIn?: boolean } = {},
): Promise<LibraryTestbed> {
  localStorage.clear();
  const account = signal<AccountDto | null>(options.signedIn ? { id: '1', pseudo: 'Me' } : null);
  const rulesets = options.rulesets ?? [new FakeRuleset()];
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'library', data: { breadcrumb: 'Bibliothèque' }, children: LIBRARY_ROUTES },
        { path: 'login', children: [] },
      ]),
      {
        provide: AuthService,
        useValue: { account, isAuthenticated: () => account() !== null },
      },
      {
        provide: BreakpointObserver,
        useValue: { observe: () => of({ matches: false, breakpoints: {} }) },
      },
      ...rulesets.map((ruleset) => ({ provide: RULESETS, multi: true, useValue: ruleset })),
    ],
  });
  const harness = await RouterTestingHarness.create();
  const render = async () => {
    harness.detectChanges();
    await harness.fixture.whenStable();
  };
  return {
    harness,
    router: TestBed.inject(Router),
    root: harness.fixture.nativeElement as HTMLElement,
    account,
    render,
    go: async (url: string) => {
      await harness.navigateByUrl(url);
      await render();
    },
  };
}
