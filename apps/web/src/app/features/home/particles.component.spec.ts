import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ParticleKind, ParticlesComponent } from './particles.component';

describe('ParticlesComponent', () => {
  let listeners: ((event: MediaQueryListEvent) => void)[];
  let removed: number;

  function preferReducedMotion(matches: boolean): void {
    listeners = [];
    removed = 0;
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: query === '(prefers-reduced-motion: reduce)' && matches,
        addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) =>
          listeners.push(listener),
        removeEventListener: () => removed++,
      })),
    );
  }

  async function render(
    kind: ParticleKind,
    active = true,
  ): Promise<ComponentFixture<ParticlesComponent>> {
    const fixture = TestBed.createComponent(ParticlesComponent);
    fixture.componentRef.setInput('kind', kind);
    fixture.componentRef.setInput('active', active);
    await fixture.whenStable();
    return fixture;
  }

  function count(fixture: ComponentFixture<ParticlesComponent>, selector: string): number {
    return (fixture.nativeElement as HTMLElement).querySelectorAll(selector).length;
  }

  afterEach(() => vi.unstubAllGlobals());

  it('particles render 9 dust motes and 12 embers', async () => {
    preferReducedMotion(false);

    const dust = await render('dust');
    const embers = await render('embers');

    expect(count(dust, '.mote')).toBe(9);
    expect(count(dust, '.ember')).toBe(0);
    expect(count(embers, '.ember')).toBe(12);
    expect(count(embers, '.ember > i')).toBe(12);
    expect(count(embers, '.mote')).toBe(0);
  });

  it('gives each ember its own sway, rise and delay', async () => {
    preferReducedMotion(false);

    const fixture = await render('embers');
    const first = (fixture.nativeElement as HTMLElement).querySelector('.ember') as HTMLElement;

    expect(first.style.left).toBe('7%');
    expect(first.style.getPropertyValue('--amp')).toBe('10px');
    expect(first.style.getPropertyValue('--sw')).toBe('0.9s');
    expect(first.style.getPropertyValue('--dur')).toBe('3.2s');
    expect(first.style.getPropertyValue('--d')).toBe('0.2s');
  });

  it('moves only while active', async () => {
    preferReducedMotion(false);

    const fixture = await render('dust', false);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.classList).not.toContain('is-active');

    fixture.componentRef.setInput('active', true);
    await fixture.whenStable();
    expect(host.classList).toContain('is-active');
  });

  it('particles render nothing when reduced motion is preferred', async () => {
    preferReducedMotion(true);

    const dust = await render('dust');
    const embers = await render('embers');

    expect(count(dust, '.mote')).toBe(0);
    expect(count(embers, '.ember')).toBe(0);
  });

  it('follows a change of the motion preference and stops listening when destroyed', async () => {
    preferReducedMotion(false);
    const fixture = await render('dust');

    listeners.forEach((listener) => listener({ matches: true } as MediaQueryListEvent));
    await fixture.whenStable();
    expect(count(fixture, '.mote')).toBe(0);

    fixture.destroy();
    expect(removed).toBe(1);
  });

  it('keeps the particles when the browser cannot tell the preference', async () => {
    vi.stubGlobal('matchMedia', undefined);

    expect(count(await render('dust'), '.mote')).toBe(9);
  });
});
