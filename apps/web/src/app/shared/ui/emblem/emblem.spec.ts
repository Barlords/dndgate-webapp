import { TestBed } from '@angular/core/testing';
import { Emblem } from './emblem';

describe('Emblem', () => {
  it('shows the replaceable emblem file, decorative by default', async () => {
    const fixture = TestBed.createComponent(Emblem);
    await fixture.whenStable();

    const img = (fixture.nativeElement as HTMLElement).querySelector('img') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe('/images/emblem.svg');
    expect(img.getAttribute('alt')).toBe('');

    fixture.componentRef.setInput('alt', 'DnDGate, retour à l’accueil');
    await fixture.whenStable();
    expect(img.getAttribute('alt')).toBe('DnDGate, retour à l’accueil');
  });
});
