import { Dictionary, Language } from '../i18n/dictionary';

/** A text keyed by an id the API may send: an unknown id is shown as it is. */
function lookup(texts: Record<string, string>, id: string): string {
  return Object.hasOwn(texts, id) ? texts[id] : id;
}

/** Name of a plan; an id the dictionary does not know is shown as it is. */
export function planName(id: string, t: Dictionary): string {
  return lookup(t.plans.names, id);
}

export function limitName(key: string, t: Dictionary): string {
  return lookup(t.plans.limits, key);
}

export function featureName(key: string, t: Dictionary): string {
  return lookup(t.plans.features, key);
}

/** Prices are cents; the currency comes from the catalog and the format from the reader's language. */
export function formatPrice(cents: number, currency: string, language: Language): string {
  return new Intl.NumberFormat(language, { style: 'currency', currency }).format(cents / 100);
}
