import { BreakpointObserver } from '@angular/cdk/layout';
import { inject, Injectable, signal } from '@angular/core';
import { SafeStorage } from '../core/storage/safe-storage';

const STORAGE_KEY = 'dg.sidebar.collapsed';
const SMALL_SCREEN = '(max-width: 767.98px)';

/**
 * Whether the sidebar is collapsed. Open by default on a large screen, collapsed under 768 px
 * (where, once opened, it is a drawer over the content). A choice made with the toggle is
 * remembered; the screen size only changes it when it crosses the 768 px line.
 */
@Injectable({ providedIn: 'root' })
export class SidebarStateService {
  private readonly storage = inject(SafeStorage);

  private readonly smallState = signal(false);
  private readonly collapsedState = signal(false);

  readonly small = this.smallState.asReadonly();
  readonly collapsed = this.collapsedState.asReadonly();

  constructor() {
    let first = true;
    inject(BreakpointObserver)
      .observe(SMALL_SCREEN)
      .subscribe(({ matches }) => {
        this.smallState.set(matches);
        this.collapsedState.set(
          first && !matches ? this.storage.get(STORAGE_KEY) === 'true' : matches,
        );
        first = false;
      });
  }

  toggle(): void {
    const next = !this.collapsedState();
    this.collapsedState.set(next);
    this.storage.set(STORAGE_KEY, String(next));
  }

  /** Closes without remembering it (a drawer closing after a navigation). */
  collapse(): void {
    this.collapsedState.set(true);
  }
}
