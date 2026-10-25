import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import {
  addDays,
  addMonths,
  compareIso,
  daysInMonth,
  formatDateLong,
  formatMonth,
  mondayIndex,
  toIso,
} from './iso-date';
import { I18n } from '../../../core/i18n/i18n.service';

interface Day {
  iso: string;
  day: number;
}

/** The API accepts years from 1900 to 2200. */
const MIN_DATE = '1900-01-01';
const MAX_DATE = '2200-12-31';

function todayIso(): string {
  const now = new Date();
  return toIso(now.getFullYear(), now.getMonth() + 1, now.getDate());
}

/**
 * Monthly calendar on parchment to pick several days (`YYYY-MM-DD`). `allowed`, when given,
 * restricts the days that can be clicked. At most `max` days can be picked. Arrow keys move
 * between days, Home/End go to the ends of the week, PageUp/PageDown change month.
 */
@Component({
  selector: 'dg-date-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(keydown)': 'onKeydown($event)' },
  template: `
    <div class="head">
      <button
        type="button"
        class="nav"
        [attr.aria-label]="t().common.datePicker.previousMonth"
        (click)="shiftMonth(-1)"
      >
        ‹
      </button>
      <h3 class="title" aria-live="polite">{{ monthLabel() }}</h3>
      <button
        type="button"
        class="nav"
        [attr.aria-label]="t().common.datePicker.nextMonth"
        (click)="shiftMonth(1)"
      >
        ›
      </button>
    </div>
    <table role="grid" [attr.aria-label]="monthLabel()" aria-multiselectable="true">
      <thead>
        <tr>
          @for (weekday of t().common.datePicker.weekdays; track weekday[0]) {
            <th scope="col" [attr.abbr]="weekday[1]">{{ weekday[0] }}</th>
          }
        </tr>
      </thead>
      <tbody>
        @for (week of weeks(); track $index) {
          <tr>
            @for (cell of week; track $index) {
              @if (cell; as day) {
                <td
                  role="gridcell"
                  [attr.aria-selected]="isSelected(day.iso)"
                  [attr.aria-disabled]="isDisabled(day.iso) ? 'true' : null"
                >
                  <button
                    type="button"
                    class="day"
                    [class.day--selected]="isSelected(day.iso)"
                    [class.day--today]="day.iso === today"
                    [attr.data-date]="day.iso"
                    [attr.aria-label]="label(day.iso)"
                    [attr.tabindex]="day.iso === tabStop() ? 0 : -1"
                    [attr.aria-disabled]="isDisabled(day.iso) ? 'true' : null"
                    (click)="toggle(day.iso)"
                    (focus)="focusDate.set(day.iso)"
                  >
                    {{ day.day }}
                  </button>
                </td>
              } @else {
                <td aria-hidden="true"></td>
              }
            }
          </tr>
        }
      </tbody>
    </table>
    <p class="count">{{ t().common.datePicker.count(selected().length, max()) }}</p>
  `,
  styles: `
    :host {
      display: block;
      max-width: 22rem;
      padding: var(--dg-space-3);
      color: var(--dg-ink);
      background: linear-gradient(180deg, var(--dg-parchment-0), var(--dg-parchment-1));
      border: 1px solid var(--dg-parchment-border);
      border-radius: var(--dg-radius);
      box-shadow: inset 0 0 18px var(--dg-parchment-shadow);
    }

    .head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: var(--dg-space-2);
    }

    .title {
      margin: 0;
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-md);
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }

    .nav,
    .day {
      font: inherit;
      color: inherit;
      cursor: pointer;
      background: transparent;
      border: 1px solid transparent;
      border-radius: var(--dg-radius-sm);
    }

    .nav {
      width: 2.25rem;
      height: 2.25rem;
      font-size: var(--dg-text-lg);
      line-height: 1;
      border-color: var(--dg-parchment-border);
    }

    table {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
    }

    th {
      padding-bottom: var(--dg-space-1);
      font-family: var(--dg-font-title);
      font-size: var(--dg-text-sm);
      font-weight: 500;
      color: var(--dg-ink-muted);
    }

    td {
      padding: 1px;
      text-align: center;
    }

    .day {
      width: 100%;
      min-height: 2.25rem;
    }

    .day:hover:not([aria-disabled='true']),
    .nav:hover {
      background: var(--dg-parchment-shadow);
    }

    .day:focus-visible,
    .nav:focus-visible {
      outline: 2px solid var(--dg-blood);
      outline-offset: 1px;
    }

    .day--today {
      border-color: var(--dg-ink-muted);
    }

    .day--selected {
      font-weight: 700;
      color: var(--dg-text-light);
      background: var(--dg-blood);
      border-color: var(--dg-gold);
    }

    .day--selected:hover:not([aria-disabled='true']) {
      background: var(--dg-blood-bright);
    }

    .day[aria-disabled='true'] {
      cursor: not-allowed;
      opacity: 0.35;
    }

    .count {
      margin: var(--dg-space-2) 0 0;
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
      text-align: right;
    }
  `,
})
export class DatePicker {
  protected readonly t = inject(I18n).t;

  /** Only « multiple » exists today; the input keeps the contract open. */
  readonly mode = input<'multiple'>('multiple');
  readonly selected = input<readonly string[]>([]);
  /** When set, the only days that can be clicked. */
  readonly allowed = input<readonly string[] | null>(null);
  readonly max = input(100);
  readonly selectedChange = output<string[]>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly today = todayIso();

  /** First day of the month the reader navigated to, or null to follow the selection. */
  private readonly month = signal<string | null>(null);
  protected readonly focusDate = signal<string>(this.today);

  private readonly firstOfMonth = computed(() => {
    const month = this.month();
    if (month !== null) {
      return month;
    }
    const first = [...this.selected()].sort(compareIso)[0] ?? this.today;
    return first.slice(0, 8) + '01';
  });

  /** The one day reachable with Tab: the focused day when visible, else the first usable one. */
  protected readonly tabStop = computed(() => {
    const first = this.firstOfMonth();
    const focus = this.focusDate();
    if (focus.slice(0, 7) === first.slice(0, 7)) {
      return focus;
    }
    const days = this.weeks()
      .flat()
      .filter((d): d is Day => d !== null);
    return (days.find((d) => !this.isDisabled(d.iso)) ?? days[0]).iso;
  });

  protected readonly monthLabel = computed(() =>
    formatMonth(this.firstOfMonth(), this.t().common.locale),
  );

  protected readonly weeks = computed<(Day | null)[][]>(() => {
    const first = this.firstOfMonth();
    const year = Number(first.slice(0, 4));
    const monthNumber = Number(first.slice(5, 7));
    const cells: (Day | null)[] = Array.from({ length: mondayIndex(first) }, () => null);
    for (let day = 1; day <= daysInMonth(year, monthNumber); day++) {
      cells.push({ iso: toIso(year, monthNumber, day), day });
    }
    while (cells.length % 7 !== 0) {
      cells.push(null);
    }
    const weeks: (Day | null)[][] = [];
    for (let i = 0; i < cells.length; i += 7) {
      weeks.push(cells.slice(i, i + 7));
    }
    return weeks;
  });

  protected isSelected(iso: string): boolean {
    return this.selected().includes(iso);
  }

  protected isDisabled(iso: string): boolean {
    if (iso < MIN_DATE || iso > MAX_DATE) {
      return true;
    }
    const allowed = this.allowed();
    if (allowed !== null && !allowed.includes(iso)) {
      return true;
    }
    // At the limit, only the days already picked remain clickable (to remove them).
    return !this.isSelected(iso) && this.selected().length >= this.max();
  }

  protected label(iso: string): string {
    return formatDateLong(iso, this.t().common.locale);
  }

  protected shiftMonth(delta: number): void {
    const next = addMonths(this.firstOfMonth(), delta);
    this.month.set(next);
    this.focusDate.set(addMonths(this.focusDate(), delta));
  }

  protected toggle(iso: string): void {
    if (this.isDisabled(iso)) {
      return;
    }
    this.focusDate.set(iso);
    const selected = this.selected();
    this.selectedChange.emit(
      selected.includes(iso)
        ? selected.filter((d) => d !== iso)
        : [...selected, iso].sort(compareIso),
    );
  }

  protected onKeydown(event: KeyboardEvent): void {
    const target = (event.target as HTMLElement).dataset['date'];
    if (target === undefined) {
      return;
    }
    const moves: Record<string, string> = {
      ArrowLeft: addDays(target, -1),
      ArrowRight: addDays(target, 1),
      ArrowUp: addDays(target, -7),
      ArrowDown: addDays(target, 7),
      Home: addDays(target, -mondayIndex(target)),
      End: addDays(target, 6 - mondayIndex(target)),
      PageUp: addMonths(target, -1),
      PageDown: addMonths(target, 1),
    };
    const next = moves[event.key];
    if (next === undefined || next < MIN_DATE || next > MAX_DATE) {
      return;
    }
    event.preventDefault();
    this.focusDate.set(next);
    this.month.set(next.slice(0, 8) + '01');
    // The day may belong to another month, rendered after this event.
    setTimeout(() =>
      this.host.nativeElement.querySelector<HTMLElement>(`[data-date="${next}"]`)?.focus(),
    );
  }
}
