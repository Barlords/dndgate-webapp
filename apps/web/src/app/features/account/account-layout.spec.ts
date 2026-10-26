import { Location } from '@angular/common';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Navigation, provideRouter, Router } from '@angular/router';
import { AccountLayout } from './account-layout';

@Component({
  imports: [AccountLayout],
  template: `<dg-account-layout heading="Connexion"
    ><p class="content">Formulaire</p></dg-account-layout
  >`,
})
class Host {}

describe('AccountLayout', () => {
  let fixture: ComponentFixture<Host>;
  let root: HTMLElement;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Host);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  const back = () => root.querySelector<HTMLButtonElement>('.dg-account__back')!;

  it('the_emblem_leads_back_to_the_home_page', () => {
    const home = root.querySelector<HTMLAnchorElement>('a.dg-account__home')!;
    expect(home.getAttribute('href')).toBe('/');
    expect(home.getAttribute('aria-label')).toBe("DnDGate, retour à l'accueil");
    expect(home.querySelector('dg-emblem')).not.toBeNull();
  });

  it('the_back_button_returns_to_the_previous_page_of_the_site', () => {
    Object.defineProperty(TestBed.inject(Router), 'lastSuccessfulNavigation', {
      get: () => signal({ previousNavigation: {} } as Navigation),
    });
    const location = vi.spyOn(TestBed.inject(Location), 'back').mockImplementation(() => undefined);
    expect(back().textContent?.trim()).toBe('Retour');
    back().click();
    expect(location).toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('the_back_button_goes_home_when_the_page_was_opened_directly', () => {
    const location = vi.spyOn(TestBed.inject(Location), 'back');
    back().click();
    expect(location).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('puts_the_terms_and_the_copyright_under_the_card', () => {
    const card = root.querySelector('dg-parchment')!;
    expect(card.querySelector('.content')?.textContent).toBe('Formulaire');
    const footer = card.nextElementSibling!;
    expect(footer.tagName).toBe('DG-SITE-FOOTER');
    expect(footer.querySelector('a')?.getAttribute('href')).toBe('/terms');
    expect(footer.textContent).toContain('© DnDGate. Tous droits réservés.');
  });
});
