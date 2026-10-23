import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Spinner } from './spinner';

describe('Spinner', () => {
  let fixture: ComponentFixture<Spinner>;
  let host: HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(Spinner);
    host = fixture.nativeElement as HTMLElement;
  });

  it('announces that something is loading', async () => {
    await fixture.whenStable();

    expect(host.getAttribute('role')).toBe('status');
    expect(host.textContent?.trim()).toBe('Chargement…');
  });

  it('stays silent with an empty label', async () => {
    fixture.componentRef.setInput('label', '');
    await fixture.whenStable();

    expect(host.getAttribute('role')).toBeNull();
    expect(host.textContent?.trim()).toBe('');
  });
});
