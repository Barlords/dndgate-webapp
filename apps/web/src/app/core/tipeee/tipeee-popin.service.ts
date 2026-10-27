import { DOCUMENT, Injectable, inject } from '@angular/core';

/** The script Tipeee gives for its widgets. */
export const TIPEEE_WIDGET_URL = 'https://plugin.tipeee.com/widget.js';

/** What the Tipeee script puts on `window` once it has run. */
interface TipeeeWidget {
  open(url: string): void;
}

type TipeeeWindow = Window & { $tipeeePopin?: TipeeeWidget };

/**
 * The Tipeee pop-in (« cart-simple » widget): the reader tips without leaving the site.
 *
 * Tipeee's script only scans the page once, when it loads, for the links to turn into pop-ins; in
 * this application the sidebar link may be drawn after that. So the link calls `open` itself, with
 * the same address the widget builds, and the 430 KB script is only fetched on the first click. If
 * it cannot load, the project page opens in a new tab instead.
 */
@Injectable({ providedIn: 'root' })
export class TipeeePopin {
  private readonly document = inject(DOCUMENT);
  private loading: Promise<TipeeeWidget> | null = null;

  /** `projectUrl` without a trailing slash, e.g. `https://fr.tipeee.com/barlords`. */
  open(projectUrl: string): void {
    const view = this.view();
    const cart =
      `${projectUrl}/cart?originUrl=${encodeURIComponent(view.location.href)}` +
      '&orientation=line&rewards=1';
    this.load().then(
      (widget) => widget.open(cart),
      () => view.open(projectUrl, '_blank', 'noopener,noreferrer'),
    );
  }

  /**
   * Back from a tip, Tipeee adds `tipeeeSuccess` to the address: its script, when it runs, shows
   * the thanks and removes the parameter. Called once when the application starts.
   */
  resumeAfterTip(): void {
    if (new URL(this.view().location.href).searchParams.has('tipeeeSuccess')) {
      this.load().catch(() => undefined);
    }
  }

  private load(): Promise<TipeeeWidget> {
    this.loading ??= new Promise<TipeeeWidget>((resolve, reject) => {
      const view = this.view();
      if (view.$tipeeePopin) {
        resolve(view.$tipeeePopin);
        return;
      }
      const script = this.document.createElement('script');
      script.src = TIPEEE_WIDGET_URL;
      script.async = true;
      script.charset = 'utf-8';
      script.onload = () =>
        view.$tipeeePopin ? resolve(view.$tipeeePopin) : reject(new Error('Tipeee widget'));
      script.onerror = () => reject(new Error('Tipeee widget'));
      this.document.head.appendChild(script);
    }).catch((error: unknown) => {
      // A failed load (offline, blocked) is tried again on the next click.
      this.loading = null;
      throw error;
    });
    return this.loading;
  }

  private view(): TipeeeWindow {
    return this.document.defaultView as TipeeeWindow;
  }
}
