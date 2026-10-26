import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TermsPage } from './terms.page';

const SECTIONS = [
  'Éditeur',
  'Objet',
  'Offres et prix',
  'Durée et renouvellement',
  'Paiement',
  'Résiliation',
  'Droit de rétractation et renonciation',
  'Suppression automatique de l’historique des messages',
  'Données personnelles',
  'Médiation',
  'Droit applicable',
];

describe('TermsPage', () => {
  async function render(): Promise<HTMLElement> {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(TermsPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('every_section_is_present_and_marked_to_complete', async () => {
    const element = await render();
    const sections = Array.from(element.querySelectorAll('section'));
    expect(sections.map((section) => section.querySelector('h2')?.textContent?.trim())).toEqual(
      SECTIONS,
    );
    for (const section of sections) {
      expect(section.querySelector('.terms__todo')?.textContent?.trim()).toBe('À compléter');
    }
  });

  it('says_that_the_text_is_a_draft_to_validate', async () => {
    const element = await render();
    expect(element.querySelector('[role="note"]')?.textContent).toContain('brouillon à valider');
  });

  it('links_back_to_the_offers', async () => {
    const element = await render();
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/plans');
  });
});
