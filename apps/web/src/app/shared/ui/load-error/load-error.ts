import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { I18n } from '../../../core/i18n/i18n.service';
import { ApiProblem } from '../../../core/errors/api-problem';
import { Button } from '../button/button';

/**
 * The inline state of a page that failed to load: the message, the problem's correlation id when
 * it has one (selectable, to quote to support), and a retry button.
 */
@Component({
  selector: 'dg-load-error',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button],
  template: `
    <p role="alert">{{ message() }}</p>
    @if (problem()?.correlationId; as reference) {
      <p class="load-error__reference">
        {{ t().common.errors.reference }} <code>{{ reference }}</code>
      </p>
    }
    <dg-button variant="ghost" (click)="retry.emit()">{{ t().common.actions.retry }}</dg-button>
  `,
  styles: `
    :host {
      display: contents;
    }
    .load-error__reference {
      font-size: var(--dg-text-sm);
      color: var(--dg-ink-muted);
    }
    .load-error__reference code {
      font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
      user-select: all;
      overflow-wrap: anywhere;
    }
  `,
})
export class LoadError {
  protected readonly t = inject(I18n).t;

  readonly message = input.required<string>();
  readonly problem = input<ApiProblem | null>(null);
  readonly retry = output<void>();
}
