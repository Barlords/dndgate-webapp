import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Paginator } from './paginator';

describe('Paginator', () => {
  let fixture: ComponentFixture<Paginator>;
  let emitted: number[];

  async function render(page: number, totalPages: number): Promise<HTMLElement> {
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('totalPages', totalPages);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function button(element: HTMLElement, label: string): HTMLButtonElement {
    return element.querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement;
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(Paginator);
    emitted = [];
    fixture.componentInstance.pageChange.subscribe((page) => emitted.push(page));
  });

  it('hides itself with one page', async () => {
    const element = await render(0, 1);

    expect(element.querySelector('nav')).toBeNull();
  });

  it('hides itself when there is nothing to page', async () => {
    const element = await render(0, 0);

    expect(element.querySelector('nav')).toBeNull();
  });

  it('shows the current page counted from one', async () => {
    const element = await render(1, 7);

    expect(element.querySelector('nav')?.textContent).toContain('Page 2 sur 7');
  });

  it('emits the neighbouring pages', async () => {
    const element = await render(2, 7);

    button(element, 'Page précédente').click();
    button(element, 'Page suivante').click();

    expect(emitted).toEqual([1, 3]);
  });

  it('cannot go before the first or after the last page', async () => {
    let element = await render(0, 3);
    expect(button(element, 'Page précédente').disabled).toBe(true);
    expect(button(element, 'Page suivante').disabled).toBe(false);

    element = await render(2, 3);
    expect(button(element, 'Page précédente').disabled).toBe(false);
    expect(button(element, 'Page suivante').disabled).toBe(true);
  });
});
