import { TestBed } from '@angular/core/testing';
import { Brand } from './brand';

describe('Brand', () => {
  it('shows the name between two emblems, hidden from assistive technologies', async () => {
    const fixture = TestBed.createComponent(Brand);
    await fixture.whenStable();

    const host = fixture.nativeElement as HTMLElement;
    expect([...host.children].map((child) => child.tagName.toLowerCase())).toEqual([
      'dg-emblem',
      'span',
      'dg-emblem',
    ]);
    expect(host.textContent?.trim()).toBe('DNDGATE');
    expect(host.getAttribute('aria-hidden')).toBe('true');
  });
});
