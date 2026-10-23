import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DatePicker } from './date-picker';
import { addDays, addMonths, formatDate, mondayIndex } from './iso-date';

describe('DatePicker', () => {
  let fixture: ComponentFixture<DatePicker>;
  let emitted: string[][];
  const el = () => fixture.nativeElement as HTMLElement;
  const day = (iso: string) => el().querySelector<HTMLButtonElement>(`[data-date="${iso}"]`)!;

  async function render(inputs: Record<string, unknown>) {
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(DatePicker);
    emitted = [];
    fixture.componentInstance.selectedChange.subscribe((dates) => emitted.push(dates));
  });

  it('date picker toggles dates and respects allowed days', async () => {
    await render({ selected: ['2026-11-14'], allowed: ['2026-11-14', '2026-11-20'] });
    expect(el().querySelector('.title')!.textContent).toContain('novembre 2026');
    expect(day('2026-11-14').closest('td')!.getAttribute('aria-selected')).toBe('true');
    expect(day('2026-11-20').closest('td')!.getAttribute('aria-selected')).toBe('false');

    day('2026-11-20').click();
    expect(emitted).toEqual([['2026-11-14', '2026-11-20']]);
    day('2026-11-14').click();
    expect(emitted[1]).toEqual([]);

    expect(day('2026-11-15').disabled).toBe(true);
    expect(day('2026-11-15').closest('td')!.getAttribute('aria-disabled')).toBe('true');
    day('2026-11-15').click();
    expect(emitted.length).toBe(2);
  });

  it('weeks start on Monday and months have French names and navigation labels', async () => {
    await render({ selected: ['2026-11-14'] });
    const heads = [...el().querySelectorAll('th')].map((th) => th.textContent!.trim());
    expect(heads).toEqual(['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim']);
    // 1 November 2026 is a Sunday: six empty cells come first.
    expect(el().querySelector('tbody tr')!.querySelectorAll('td[aria-hidden]').length).toBe(6);
    (el().querySelector('[aria-label="Mois suivant"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el().querySelector('.title')!.textContent).toContain('décembre 2026');
    (el().querySelector('[aria-label="Mois précédent"]') as HTMLButtonElement).click();
    (el().querySelector('[aria-label="Mois précédent"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el().querySelector('.title')!.textContent).toContain('octobre 2026');
  });

  it('arrow keys move the focus, across months', async () => {
    {
      await render({ selected: ['2026-11-30'] });
      const press = async (from: string, key: string) => {
        day(from).dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
        await fixture.whenStable();
        await new Promise((resolve) => setTimeout(resolve));
      };
      await press('2026-11-30', 'ArrowRight');
      expect(day('2026-12-01').getAttribute('tabindex')).toBe('0');
      await press('2026-12-01', 'ArrowDown');
      expect(day('2026-12-08').getAttribute('tabindex')).toBe('0');
      await press('2026-12-08', 'Home');
      expect(day('2026-12-07').getAttribute('tabindex')).toBe('0');
      await press('2026-12-07', 'End');
      expect(day('2026-12-13').getAttribute('tabindex')).toBe('0');
      await press('2026-12-13', 'PageUp');
      expect(day('2026-11-13').getAttribute('tabindex')).toBe('0');
      await press('2026-11-13', 'ArrowLeft');
      await press('2026-11-12', 'ArrowUp');
      expect(day('2026-11-05').getAttribute('tabindex')).toBe('0');
    }
  });

  it('stops adding at the maximum but still removes', async () => {
    await render({ selected: ['2026-11-14'], max: 1 });
    expect(day('2026-11-15').disabled).toBe(true);
    expect(day('2026-11-14').disabled).toBe(false);
  });

  it('formats and computes dates without timezone drift', () => {
    expect(formatDate('2026-11-14')).toBe('sam. 14 nov. 2026');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28');
    expect(addMonths('2026-01-15', -1)).toBe('2025-12-15');
    expect(mondayIndex('2026-11-16')).toBe(0);
  });
});
