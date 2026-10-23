import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AccountDto } from '../../core/api/generated/models/account-dto';
import { AuthService } from '../../core/auth/auth.service';
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
    <dg-shell title="BIBLIOTHÈQUE" [groups]="groups">
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

  it('centres the emblem and the name in a bar linking to the home page', async () => {
    await render();
    const brand = element.querySelector<HTMLAnchorElement>('.topbar__brand')!;
    expect(brand.getAttribute('href')).toBe('/');
    expect(brand.textContent).toContain('DNDGATE');
    expect(brand.querySelector('dg-emblem')).not.toBeNull();
  });

  it('projects the sidebar header and the content', async () => {
    await render();
    expect(element.querySelector('.sidebar .header-slot')?.textContent).toBe('Jeux de règles');
    expect(element.querySelector('main .content-slot')?.textContent).toBe('Contenu');
    expect(element.querySelector('.sidebar__title')?.textContent).toBe('BIBLIOTHÈQUE');
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
    expect(element.querySelector('.sidebar__header')).toBeNull();
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
