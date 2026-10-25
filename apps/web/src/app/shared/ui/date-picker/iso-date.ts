/** Calendar dates as `YYYY-MM-DD` strings, handled in UTC so no timezone can shift a day. */
const DAY_MS = 86_400_000;

const LONG_FORMAT: Intl.DateTimeFormatOptions = {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
};

const FULL_FORMAT: Intl.DateTimeFormatOptions = {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
};

const MONTH_FORMAT: Intl.DateTimeFormatOptions = {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
};

/** One formatter per locale and format, made the first time it is needed. */
const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(locale: string, name: string, options: Intl.DateTimeFormatOptions) {
  const key = `${locale}|${name}`;
  let found = formatters.get(key);
  if (!found) {
    found = new Intl.DateTimeFormat(locale, options);
    formatters.set(key, found);
  }
  return found;
}

function pad(value: number, width: number): string {
  return String(value).padStart(width, '0');
}

export function toIso(year: number, month: number, day: number): string {
  return `${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}`;
}

/** The instant of midnight UTC of `iso`. */
function toTime(iso: string): number {
  const [year, month, day] = iso.split('-').map(Number);
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  return date.getTime();
}

function fromTime(time: number): string {
  const date = new Date(time);
  return toIso(date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate());
}

export function addDays(iso: string, days: number): string {
  return fromTime(toTime(iso) + days * DAY_MS);
}

export function daysInMonth(year: number, month: number): number {
  const date = new Date(0);
  date.setUTCFullYear(year, month, 0);
  return date.getUTCDate();
}

/** Moves by whole months, keeping the day when the target month is long enough. */
export function addMonths(iso: string, months: number): string {
  const [year, month, day] = iso.split('-').map(Number);
  const index = year * 12 + (month - 1) + months;
  const targetYear = Math.floor(index / 12);
  const targetMonth = (index % 12) + 1;
  return toIso(targetYear, targetMonth, Math.min(day, daysInMonth(targetYear, targetMonth)));
}

/** Day of the week, 0 for Monday to 6 for Sunday. */
export function mondayIndex(iso: string): number {
  return (new Date(toTime(iso)).getUTCDay() + 6) % 7;
}

/** « sam. 14 nov. 2026 » in `fr-FR`. */
export function formatDate(iso: string, locale = 'fr-FR'): string {
  return formatter(locale, 'long', LONG_FORMAT).format(toTime(iso));
}

/** « samedi 14 novembre 2026 » in `fr-FR`. */
export function formatDateLong(iso: string, locale = 'fr-FR'): string {
  return formatter(locale, 'full', FULL_FORMAT).format(toTime(iso));
}

/** « novembre 2026 » in `fr-FR`. */
export function formatMonth(iso: string, locale = 'fr-FR'): string {
  return formatter(locale, 'month', MONTH_FORMAT).format(toTime(iso));
}

export function compareIso(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}
