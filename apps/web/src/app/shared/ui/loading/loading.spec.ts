import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DEFAULT_LOADING_MESSAGE, Loading } from './loading';

describe('Loading', () => {
  let fixture: ComponentFixture<Loading>;
  let host: HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(Loading);
    host = fixture.nativeElement as HTMLElement;
  });

  const message = () => host.querySelector('.loading__message');

  it('shows the default flavour under a spinner and announces the plain label', async () => {
    await fixture.whenStable();

    expect(host.getAttribute('role')).toBe('status');
    expect(host.querySelector('dg-spinner')).not.toBeNull();
    expect(message()?.textContent).toBe(DEFAULT_LOADING_MESSAGE);
    expect(message()?.getAttribute('aria-hidden')).toBe('true');
    expect(host.querySelector('.dg-visually-hidden')?.textContent).toBe('Chargement…');
  });

  it('shows the flavour and announces the label it is given', async () => {
    fixture.componentRef.setInput('message', 'Demande en cours à Mystra…');
    fixture.componentRef.setInput('label', 'Chargement des sorts');
    await fixture.whenStable();

    expect(message()?.textContent).toBe('Demande en cours à Mystra…');
    expect(host.querySelector('.dg-visually-hidden')?.textContent).toBe('Chargement des sorts');
    // The inner spinner stays silent: one status, not two.
    expect(host.querySelector('dg-spinner')?.getAttribute('role')).toBeNull();
  });
});
