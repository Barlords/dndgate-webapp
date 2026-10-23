import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HomePage } from './home.page';

describe('HomePage', () => {
  let fixture: ComponentFixture<HomePage>;
  let element: HTMLElement;
  let navigate: ReturnType<typeof vi.spyOn>;
  const isAuthenticated = signal(false);

  beforeEach(async () => {
    isAuthenticated.set(false);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isAuthenticated } }],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(HomePage);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  function halves(): HTMLElement[] {
    return Array.from(element.querySelectorAll<HTMLElement>('dg-home-half'));
  }

  function link(index: number): HTMLAnchorElement {
    return halves()[index].querySelector('a') as HTMLAnchorElement;
  }

  function pointer(type: string, target: Element, pointerType: string): void {
    target.dispatchEvent(Object.assign(new Event(type, { bubbles: true }), { pointerType }));
  }

  // A Tab press first, as a browser only matches :focus-visible for a keyboard focus.
  async function focusWithKeyboard(index: number): Promise<void> {
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    link(index).focus();
    await fixture.whenStable();
  }

  async function tap(index: number): Promise<void> {
    const title = halves()[index].querySelector('h2') as HTMLElement;
    pointer('pointerdown', title, 'touch');
    title.click();
    await fixture.whenStable();
  }

  it('renders the library on the left and the tavern on the right', () => {
    const [library, tavern] = halves();

    expect(halves()).toHaveLength(2);
    expect(library.classList).toContain('home-half--library');
    expect(library.querySelector('h2')?.textContent).toBe('BIBLIOTHÈQUE');
    expect(library.textContent).toContain('Sorts, classes, monstres et règles');
    expect(library.textContent).toContain('ENTRER');
    expect(tavern.classList).toContain('home-half--tavern');
    expect(tavern.querySelector('h2')?.textContent).toBe('TAVERNE');
    expect(tavern.textContent).toContain('Tes aventures, ta table, tes compagnons');
    expect(tavern.textContent).toContain('S’ATTABLER');
    expect(element.querySelector('dg-emblem')).not.toBeNull();
    expect(tavern.hasAttribute('title')).toBe(false);
  });

  it('links to /library and /tavern', () => {
    expect(link(0).getAttribute('href')).toBe('/library');
    expect(link(1).getAttribute('href')).toBe('/tavern');
  });

  it('activates a half on keyboard focus', async () => {
    await focusWithKeyboard(0);

    expect(halves()[0].classList).toContain('is-active');
    expect(halves()[1].classList).toContain('is-dimmed');

    await focusWithKeyboard(1);

    expect(halves()[0].classList).not.toContain('is-active');
    expect(halves()[0].classList).toContain('is-dimmed');
    expect(halves()[1].classList).toContain('is-active');

    link(1).blur();
    await fixture.whenStable();

    expect(element.querySelector('.is-active, .is-dimmed')).toBeNull();
  });

  it('activates a half while a mouse hovers it', async () => {
    pointer('pointerenter', link(1), 'mouse');
    await fixture.whenStable();

    expect(halves()[1].classList).toContain('is-active');
    expect(halves()[0].classList).toContain('is-dimmed');

    pointer('pointerleave', link(1), 'mouse');
    await fixture.whenStable();

    expect(element.querySelector('.is-active, .is-dimmed')).toBeNull();
  });

  it('leaves touch hovering to the tap', async () => {
    pointer('pointerenter', link(0), 'touch');
    await fixture.whenStable();

    expect(element.querySelector('.is-active')).toBeNull();
  });

  it('activates on the first tap and follows the link on the second', async () => {
    await tap(0);

    expect(halves()[0].classList).toContain('is-active');
    expect(navigate).not.toHaveBeenCalled();

    await tap(0);

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(String(navigate.mock.calls[0][0])).toBe('/library');
  });

  it('ignores the focus a mouse click gives the link', async () => {
    const title = halves()[0].querySelector('h2') as HTMLElement;
    title.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    link(0).focus();
    await fixture.whenStable();

    expect(element.querySelector('.is-active')).toBeNull();
  });

  it('follows the link on the first mouse click', async () => {
    const title = halves()[1].querySelector('h2') as HTMLElement;
    pointer('pointerdown', title, 'mouse');
    title.click();
    await fixture.whenStable();

    expect(String(navigate.mock.calls[0][0])).toBe('/tavern');
  });

  it('tells a signed-out visitor that the tavern asks to sign in', async () => {
    expect(link(1).textContent).toContain('connexion requise');
    expect(link(0).textContent).not.toContain('connexion requise');

    isAuthenticated.set(true);
    await fixture.whenStable();

    expect(link(1).textContent).not.toContain('connexion requise');
  });

  it('lights the frame, the seal and the particles of the active half only', async () => {
    await focusWithKeyboard(0);

    expect(halves()[0].querySelector('dg-particles')?.classList).toContain('is-active');
    expect(halves()[1].querySelector('dg-particles')?.classList).not.toContain('is-active');
    expect(halves()[0].querySelector('.home-half__frame')).not.toBeNull();
    expect(halves()[0].querySelector('.home-half__seal')).not.toBeNull();
  });
});
