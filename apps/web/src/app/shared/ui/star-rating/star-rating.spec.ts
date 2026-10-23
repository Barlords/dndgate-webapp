import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StarRating } from './star-rating';

describe('StarRating', () => {
  let fixture: ComponentFixture<StarRating>;
  let emitted: number[];

  async function render(value: number | null, readonly: boolean): Promise<HTMLElement> {
    fixture.componentRef.setInput('value', value);
    fixture.componentRef.setInput('readonly', readonly);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function stars(element: HTMLElement): HTMLElement[] {
    return Array.from(element.querySelectorAll<HTMLElement>('.dg-star'));
  }

  function filled(element: HTMLElement): number {
    return stars(element).filter((star) => star.classList.contains('dg-star--on')).length;
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(StarRating);
    emitted = [];
    fixture.componentInstance.valueChange.subscribe((value) => emitted.push(value));
  });

  it('emits the clicked value', async () => {
    const element = await render(2, false);

    expect(stars(element)).toHaveLength(5);
    stars(element)[3].click();
    stars(element)[0].click();

    expect(emitted).toEqual([4, 1]);
  });

  it('ignores clicks when readonly', async () => {
    const element = await render(3, true);

    stars(element).forEach((star) => star.click());

    expect(emitted).toEqual([]);
    expect(element.querySelector('button')).toBeNull();
  });

  it('shows the value as filled stars', async () => {
    const element = await render(3, true);

    expect(filled(element)).toBe(3);
    expect(element.querySelector('[role="img"]')?.getAttribute('aria-label')).toBe(
      'Note : 3 sur 5',
    );
  });

  it('rounds an average to the nearest star', async () => {
    expect(filled(await render(3.6, true))).toBe(4);
    expect(filled(await render(null, true))).toBe(0);
  });

  it('previews the hovered value', async () => {
    const element = await render(1, false);

    stars(element)[2].dispatchEvent(new MouseEvent('mouseenter'));
    await fixture.whenStable();
    expect(filled(element)).toBe(3);

    element.querySelector('.dg-star-rating')?.dispatchEvent(new MouseEvent('mouseleave'));
    await fixture.whenStable();
    expect(filled(element)).toBe(1);
  });

  it('shows the value input after a click, not the clicked star', async () => {
    const element = await render(2, false);

    // A tap on a touch screen fires mouseenter then click, and no mouseleave.
    stars(element)[4].dispatchEvent(new MouseEvent('mouseenter'));
    stars(element)[4].click();
    await fixture.whenStable();

    expect(emitted).toEqual([5]);
    expect(filled(element)).toBe(2);
  });

  it('marks the chosen star for assistive technologies', async () => {
    const element = await render(2, false);

    const pressed = stars(element).map((star) => star.getAttribute('aria-checked'));
    expect(pressed).toEqual(['false', 'true', 'false', 'false', 'false']);
    expect(stars(element)[4].getAttribute('aria-label')).toBe('5 sur 5');
  });
});
