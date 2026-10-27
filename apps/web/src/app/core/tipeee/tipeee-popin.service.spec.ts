import { TestBed } from '@angular/core/testing';
import { TIPEEE_WIDGET_URL, TipeeePopin } from './tipeee-popin.service';

type TipeeeWindow = Window & { $tipeeePopin?: { open(url: string): void } };

describe('TipeeePopin', () => {
  const view = window as TipeeeWindow;
  let popin: TipeeePopin;

  function widgetScripts(): HTMLScriptElement[] {
    return [...document.head.querySelectorAll<HTMLScriptElement>('script')].filter(
      (script) => script.src === TIPEEE_WIDGET_URL,
    );
  }

  /** Lets the pending promise callbacks run. */
  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
  }

  beforeEach(() => {
    widgetScripts().forEach((script) => script.remove());
    delete view.$tipeeePopin;
    popin = TestBed.inject(TipeeePopin);
  });

  afterEach(() => {
    widgetScripts().forEach((script) => script.remove());
    delete view.$tipeeePopin;
    vi.restoreAllMocks();
  });

  it('loads_the_widget_on_the_first_click_and_opens_the_cart_of_the_project', async () => {
    const open = vi.fn();
    popin.open('https://fr.tipeee.com/barlords');

    const [script] = widgetScripts();
    expect(script).toBeDefined();
    expect(script.async).toBe(true);
    view.$tipeeePopin = { open };
    script.dispatchEvent(new Event('load'));
    await settle();

    expect(open).toHaveBeenCalledWith(
      'https://fr.tipeee.com/barlords/cart?originUrl=' +
        encodeURIComponent(window.location.href) +
        '&orientation=line&rewards=1',
    );
  });

  it('loads_the_widget_only_once', async () => {
    const open = vi.fn();
    popin.open('https://fr.tipeee.com/barlords');
    view.$tipeeePopin = { open };
    widgetScripts()[0].dispatchEvent(new Event('load'));
    await settle();

    popin.open('https://fr.tipeee.com/barlords');
    await settle();

    expect(widgetScripts()).toHaveLength(1);
    expect(open).toHaveBeenCalledTimes(2);
  });

  it('opens_the_project_page_in_a_new_tab_when_the_widget_cannot_load_and_retries_later', async () => {
    const newTab = vi.spyOn(window, 'open').mockReturnValue(null);
    popin.open('https://fr.tipeee.com/barlords');
    widgetScripts()[0].dispatchEvent(new Event('error'));
    await settle();

    expect(newTab).toHaveBeenCalledWith(
      'https://fr.tipeee.com/barlords',
      '_blank',
      'noopener,noreferrer',
    );

    widgetScripts().forEach((script) => script.remove());
    popin.open('https://fr.tipeee.com/barlords');
    expect(widgetScripts()).toHaveLength(1);
  });

  it('back_from_a_tip_the_widget_is_loaded_to_thank_the_reader', () => {
    const address = window.location.href;
    window.history.replaceState(null, '', '/?tipeeeSuccess=abc');
    try {
      popin.resumeAfterTip();
      expect(widgetScripts()).toHaveLength(1);
    } finally {
      window.history.replaceState(null, '', address);
    }
  });

  it('without_a_tip_nothing_is_loaded_at_start', () => {
    popin.resumeAfterTip();
    expect(widgetScripts()).toHaveLength(0);
  });
});
