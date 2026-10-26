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

  it('the_sidebar_has_no_plans_nor_terms_link_any_more', async () => {
    await render();
    account.set({ id: '1', pseudo: 'Barlords' });
    await fixture.whenStable();
    expect(element.querySelector('.sidebar a[href="/plans"]')).toBeNull();
    expect(element.querySelector('.sidebar a[href="/terms"]')).toBeNull();
  });

  it('offers_a_beer_on_tipeee_in_a_new_tab', async () => {
    await render();
    const beer = element.querySelector<HTMLAnchorElement>('.sidebar__footer .sidebar__beer')!;
    expect(beer.getAttribute('href')).toBe('https://fr.tipeee.com/barlords/');
    expect(beer.getAttribute('target')).toBe('_blank');
    expect(beer.getAttribute('rel')).toBe('noopener noreferrer');
    expect(beer.textContent).toContain('Offrir une bière');
    expect(beer.getAttribute('aria-label')).toBe('Offrir une bière sur Tipeee (nouvel onglet)');
    const drawn = [...beer.querySelectorAll('path')].map((path) => path.getAttribute('d'));
    expect(drawn).toEqual([...ICON_PATHS.beer]);
  });

  it('only_the_navigation_scrolls_the_title_and_the_header_stay_out_of_it', async () => {
    await render();
    const nav = element.querySelector('.sidebar__nav')!;
    expect(nav.querySelector('.header-slot')).toBeNull();
    expect(nav.querySelector('.sidebar__title')).toBeNull();
    // The header sits with the title, above the panel the toggle controls.
    const header = element.querySelector('.sidebar__header')!;
    expect(header.previousElementSibling?.classList).toContain('sidebar__heading');
    expect(header.nextElementSibling?.classList).toContain('sidebar__panel');
    expect(nav.nextElementSibling).toBe(toggle());
  });

  it('puts_the_terms_and_the_copyright_under_the_content', async () => {
    await render();
    const footer = element.querySelector('main dg-site-footer')!;
    expect(element.querySelector('main .content-slot')!.nextElementSibling).toBe(footer);
    expect(footer.querySelector('a')?.getAttribute('href')).toBe('/terms');
    expect(footer.querySelector('a')?.textContent?.trim()).toBe('CGV');
    expect(footer.textContent).toContain('© DnDGate. Tous droits réservés.');
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
