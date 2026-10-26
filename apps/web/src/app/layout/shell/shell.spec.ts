import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AccountDto } from '../../core/api/generated/models/account-dto';
import { AuthService } from '../../core/auth/auth.service';
import { ICON_PATHS } from '../../shared/ui/icon/icon-paths';
import { NavGroup } from '../nav.model';
import { Shell } from './shell';

const GROUPS: NavGroup[] = [
  {
    label: 'POUR LES JOUEURS',
    items: [
      { label: 'Classes', icon: 'book', link: '/library/classes' },
      { label: 'Sorts', icon: 'wand', link: '/library/spells', exact: true },
    ],
  },
];

@Component({
  imports: [Shell],
  template: `
    <dg-shell heading="BIBLIOTHÈQUE" [groups]="groups">
      <p sidebarHeader class="header-slot">Jeux de règles</p>
      <p class="content-slot">Contenu</p>
    </dg-shell>
  `,
})
class Host {
  readonly groups = GROUPS;
}

@Component({ template: '' })
class Stub {}

describe('Shell', () => {
  const account = signal<AccountDto | null>(null);
  let fixture: ComponentFixture<Host>;
  let element: HTMLElement;
  let small: BehaviorSubject<{ matches: boolean; breakpoints: object }>;

  async function render(isSmall = false): Promise<void> {
    localStorage.clear();
    small = new BehaviorSubject({ matches: isSmall, breakpoints: {} });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'library/spells', component: Stub, data: { breadcrumb: 'Sorts' } },
          { path: 'library/classes', component: Stub },
        ]),
        { provide: AuthService, useValue: { account } },
        { provide: BreakpointObserver, useValue: { observe: () => small } },
      ],
    });
    await TestBed.inject(Router).navigateByUrl('/library/spells');
    fixture = TestBed.createComponent(Host);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  const toggle = () => element.querySelector<HTMLButtonElement>('.sidebar__toggle')!;

  beforeEach(() => account.set(null));

  it('has no top bar: the logo, linking to the home page, tops the sidebar', async () => {
    await render();
    expect(element.querySelector('dg-topbar')).toBeNull();
    const brand = element.querySelector<HTMLAnchorElement>('.sidebar__head .sidebar__brand')!;
    expect(brand.getAttribute('href')).toBe('/');
    expect(brand.getAttribute('aria-label')).toBe("DnDGate, retour à l'accueil");
    expect(brand.textContent).toContain('DNDGATE');
    expect(brand.querySelectorAll('dg-brand dg-emblem')).toHaveLength(2);
  });

  it('leaves no title attribute on the host (no native tooltip over the whole shell)', async () => {
    await render();
    expect(element.querySelector('dg-shell')?.hasAttribute('title')).toBe(false);
  });

  it('projects the sidebar header and the content', async () => {
    await render();
    expect(element.querySelector('.sidebar .header-slot')?.textContent).toBe('Jeux de règles');
    expect(element.querySelector('main .content-slot')?.textContent).toBe('Contenu');
    expect(element.querySelector('.sidebar__title')?.textContent).toBe('BIBLIOTHÈQUE');
  });

  it('puts the title under the logo and the toggle at the bottom, just above the account', async () => {
    await render();
    expect([...element.querySelector('.sidebar__head')!.children]).toEqual([
      element.querySelector('.sidebar__brand'),
    ]);
    expect(element.querySelector('.sidebar__heading .sidebar__title')?.textContent).toBe(
      'BIBLIOTHÈQUE',
    );
    expect(toggle().nextElementSibling).toBe(element.querySelector('.sidebar__footer'));
    expect(toggle().previousElementSibling?.tagName).toBe('NAV');
    const drawn = [...toggle().querySelectorAll('path')].map((path) => path.getAttribute('d'));
    expect(drawn).toEqual([...ICON_PATHS.sidebar]);
  });

  it('folds the logo into the emblem, which still leads home', async () => {
    await render();
    toggle().click();
    await fixture.whenStable();
    const brand = element.querySelector<HTMLAnchorElement>('.sidebar__head .sidebar__brand')!;
    expect(brand.getAttribute('href')).toBe('/');
    expect(brand.getAttribute('title')).toBe("DnDGate, retour à l'accueil");
    expect(brand.querySelector('dg-brand')).toBeNull();
    expect(brand.querySelector('dg-emblem')).not.toBeNull();
    expect(toggle().getAttribute('title')).toBe('Ouvrir le sommaire');
  });

  it('lists the groups and marks the current item', async () => {
    await render();
    expect(element.querySelector('.sidebar__group')?.textContent).toBe('POUR LES JOUEURS');
    const active = element.querySelectorAll('.sidebar__link.on');
    expect(active.length).toBe(1);
    expect(active[0].textContent).toContain('Sorts');
  });

  it('shows the breadcrumb above the content', async () => {
    await render();
    const crumbs = element.querySelector('nav[aria-label="Fil d\'Ariane"]')!;
    expect(crumbs.textContent).toContain('Sorts');
    expect(crumbs.querySelector('[aria-current="page"]')?.textContent).toBe('Sorts');
  });

  it('shell shows the account or a login link', async () => {
    await render();
    const login = element.querySelector<HTMLAnchorElement>('.sidebar__footer a')!;
    expect(login.textContent).toContain('Se connecter');
    expect(login.getAttribute('href')).toBe('/login?returnUrl=%2Flibrary%2Fspells');

    account.set({ id: '1', pseudo: 'Barlords' });
    await fixture.whenStable();
    const profile = element.querySelector<HTMLAnchorElement>('.sidebar__footer a')!;
    expect(profile.getAttribute('href')).toBe('/account');
    expect(profile.textContent).toContain('Barlords');
    expect(element.querySelector('.sidebar__avatar')?.textContent?.trim()).toBe('B');
  });

  it('the_plans_link_is_there_for_everyone', async () => {
    await render();
    const plans = () =>
      element.querySelector<HTMLAnchorElement>('.sidebar__footer a[href="/plans"]');
    expect(plans()?.textContent).toContain('Offres');
    account.set({ id: '1', pseudo: 'Barlords' });
    await fixture.whenStable();
    expect(plans()?.textContent).toContain('Offres');
  });

  it('the_terms_link_is_there_for_everyone', async () => {
    await render();
    const terms = () =>
      element.querySelector<HTMLAnchorElement>('.sidebar__footer a[href="/terms"]');
    expect(terms()?.textContent).toContain('CGV');
    account.set({ id: '1', pseudo: 'Barlords' });
    await fixture.whenStable();
    expect(terms()?.textContent).toContain('CGV');
  });

  it('collapsed shell shows icons with labels as tooltips', async () => {
    await render();
    toggle().click();
    await fixture.whenStable();
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    expect(element.querySelector('.sidebar')?.classList).toContain('sidebar--collapsed');
    const link = element.querySelector<HTMLAnchorElement>('.sidebar__link')!;
    expect(link.getAttribute('title')).toBe('Classes');
    expect(link.querySelector('dg-icon')).not.toBeNull();
    expect(element.querySelector('.sidebar__title')).toBeNull();
    // The header stays (the ruleset select folds into an icon), so that the links do not move.
    expect(element.querySelector('.sidebar__header .header-slot')).not.toBeNull();
    expect(element.querySelector('.sidebar__group')?.textContent?.trim()).toBe('POUR LES JOUEURS');
  });

  it('marks the link of the page shown as the current page', async () => {
    await render();
    const current = element.querySelector('.sidebar__link[aria-current="page"]');
    expect(current?.textContent).toContain('Sorts');
    expect(current?.classList).toContain('on');
  });

  it('open shell has no tooltip and exposes the toggle state', async () => {
    await render();
    expect(toggle().getAttribute('aria-expanded')).toBe('true');
    expect(toggle().getAttribute('aria-controls')).toBe(
      element.querySelector('.sidebar__panel')?.id,
    );
    expect(element.querySelector('.sidebar__link')?.getAttribute('title')).toBeNull();
  });

  it('opens as a drawer with a veil on small screens and closes on a link click', async () => {
    await render(true);
    expect(element.querySelector('.sidebar__veil')).toBeNull();
    toggle().click();
    await fixture.whenStable();
    expect(element.querySelector('.sidebar__veil')).not.toBeNull();
    element.querySelector<HTMLAnchorElement>('.sidebar__link')!.click();
    await fixture.whenStable();
    expect(element.querySelector('.sidebar__veil')).toBeNull();
  });

  it('closes the drawer when the veil is clicked', async () => {
    await render(true);
    toggle().click();
    await fixture.whenStable();
    element.querySelector<HTMLElement>('.sidebar__veil')!.click();
    await fixture.whenStable();
    expect(element.querySelector('.sidebar__veil')).toBeNull();
  });
});
