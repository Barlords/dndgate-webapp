import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Icon } from './icon';
import { ICON_PATHS } from './icon-paths';

describe('Icon', () => {
  let fixture: ComponentFixture<Icon>;

  function svg(): SVGElement {
    return (fixture.nativeElement as HTMLElement).querySelector('svg') as SVGElement;
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(Icon);
  });

  it('draws the paths of the named icon, hidden from assistive technologies', async () => {
    fixture.componentRef.setInput('name', 'trash');
    await fixture.whenStable();

    const drawn = Array.from(svg().querySelectorAll('path')).map((path) => path.getAttribute('d'));
    expect(drawn).toEqual([...ICON_PATHS.trash]);
    expect(svg().getAttribute('aria-hidden')).toBe('true');
    expect(svg().getAttribute('fill')).toBe('none');
  });

  it('is announced when labelled', async () => {
    fixture.componentRef.setInput('name', 'logout');
    fixture.componentRef.setInput('label', 'Se déconnecter');
    await fixture.whenStable();

    expect(svg().getAttribute('role')).toBe('img');
    expect(svg().getAttribute('aria-label')).toBe('Se déconnecter');
    expect(svg().getAttribute('aria-hidden')).toBeNull();
  });

  it('can be filled', async () => {
    fixture.componentRef.setInput('name', 'star');
    fixture.componentRef.setInput('filled', true);
    await fixture.whenStable();

    expect(svg().getAttribute('fill')).toBe('currentColor');
  });

  it('covers the whole fixed set', () => {
    expect(Object.keys(ICON_PATHS).sort()).toEqual(
      [
        'book',
        'scroll',
        'sword',
        'skull',
        'wand',
        'users',
        'map',
        'calendar',
        'chat',
        'crown',
        'plus',
        'key',
        'user',
        'gear',
        'chevron-left',
        'chevron-right',
        'menu',
        'logout',
        'upload',
        'download',
        'trash',
        'edit',
        'star',
      ].sort(),
    );
  });
});
