import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
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
        Référence : <code>{{ reference }}</code>
      </p>
    }
    <dg-button variant="ghost" (click)="retry.emit()">Réessayer</dg-button>
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
  readonly message = input.required<string>();
  readonly problem = input<ApiProblem | null>(null);
  readonly retry = output<void>();
}
