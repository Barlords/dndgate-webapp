import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { I18n } from '../../../core/i18n/i18n.service';
import { Spinner } from '../spinner/spinner';

/**
 * The loader of a whole page or panel: a large spinner over a line of flavour text ("Demande en
 * cours à Mystra…"). The flavour is decoration; `label` is the plain words announced to assistive
 * technologies. Buttons keep the small `dg-spinner`. Without them, both are the dictionary's.
 */
@Component({
  selector: 'dg-loading',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Spinner],
  host: { role: 'status' },
  template: `
    <dg-spinner label="" />
    <p class="loading__message" aria-hidden="true">{{ shownMessage() }}</p>
    <span class="dg-visually-hidden">{{ shownLabel() }}</span>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dg-space-3);
      padding: var(--dg-space-4) 0;
      text-align: center;
    }

    dg-spinner {
      --dg-spinner-size: 4rem;
      // Light gold on leather, blood red on parchment: it shows on both.
      --dg-spinner-color: var(--dg-title-color);
    }

    .loading__message {
      margin: 0;
      font-style: italic;
      font-size: var(--dg-text-md);
      color: inherit;
      opacity: 0.85;
    }
  `,
})
export class Loading {
  protected readonly t = inject(I18n).t;

  readonly label = input<string | null>(null);
  readonly message = input<string | null | undefined>(null);

  protected readonly shownLabel = computed(() => this.label() ?? this.t().common.loading.label);
  protected readonly shownMessage = computed(
    () => this.message() ?? this.t().common.loading.message,
  );
}
