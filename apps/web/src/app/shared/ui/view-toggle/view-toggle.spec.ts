import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ViewToggle } from './view-toggle';

describe('ViewToggle', () => {
  let fixture: ComponentFixture<ViewToggle>;
  let host: HTMLElement;

  async function create(storageKey: string | null = null) {
    fixture = TestBed.createComponent(ViewToggle);
    fixture.componentRef.setInput('storageKey', storageKey);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  const option = (label: string) =>
    host.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`)!;
  const pressed = () =>
    [...host.querySelectorAll('button[aria-pressed="true"]')].map((b) => b.getAttribute('title'));

  beforeEach(() => localStorage.clear());

  it('offers a table and cards, with the cards chosen by default', async () => {
    await create();
    expect(host.querySelector('[role="group"]')!.getAttribute('aria-label')).toBe('Affichage');
    expect(option('Afficher en tableau').querySelector('dg-icon')).not.toBeNull();
    expect(pressed()).toEqual(['Cartes']);
  });

  it('switches the mode on a click', async () => {
    await create();
    option('Afficher en tableau').click();
    await fixture.whenStable();
    expect(fixture.componentInstance.mode()).toBe('table');
    expect(pressed()).toEqual(['Tableau']);
  });

  it('remembers the choice under its key and restores it', async () => {
    await create('dg.view.test');
    option('Afficher en tableau').click();
    expect(localStorage.getItem('dg.view.test')).toBe('table');

    await create('dg.view.test');
    expect(fixture.componentInstance.mode()).toBe('table');
  });

  it('stores nothing without a key and ignores a stored value it does not know', async () => {
    await create();
    option('Afficher en tableau').click();
    expect(localStorage.length).toBe(0);

    localStorage.setItem('dg.view.test', 'mosaic');
    await create('dg.view.test');
    expect(fixture.componentInstance.mode()).toBe('cards');
  });
});
