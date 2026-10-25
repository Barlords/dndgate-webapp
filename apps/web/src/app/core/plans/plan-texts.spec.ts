import { DICTIONARIES } from '../i18n/dictionary';
import { featureName, formatPrice, limitName, planName } from './plan-texts';

describe('plan texts', () => {
  const fr = DICTIONARIES.fr;
  const en = DICTIONARIES.en;

  it('known_ids_have_their_names_unknown_ids_show_the_id', () => {
    expect(planName('dungeon-master', fr)).toBe('Maître du Donjon');
    expect(planName('hero', en)).toBe('Hero');
    expect(planName('mystery', fr)).toBe('mystery');
    expect(limitName('spellBooks', fr)).toBe(fr.plans.limits.spellBooks);
    expect(limitName('mystery', fr)).toBe('mystery');
    expect(featureName('video', en)).toBe(en.plans.features.video);
    expect(featureName('mystery', en)).toBe('mystery');
  });

  it('prices_are_cents_in_the_reader_locale', () => {
    const plain = (text: string) => text.replace(/\s/g, ' ');
    expect(plain(formatPrice(499, 'EUR', 'fr'))).toBe('4,99 €');
    expect(plain(formatPrice(499, 'EUR', 'en'))).toBe('€4.99');
  });
});
