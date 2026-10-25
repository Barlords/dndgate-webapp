import { CdkTrapFocus } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { IconButton } from '../../../shared/ui/icon-button/icon-button';
import { ColumnDef, FilterDef } from '../rulesets/ruleset.model';
import { I18n } from '../../../core/i18n/i18n.service';

export interface FilterOption {
  value: string;
  label: string;
}

export const PAGE_SIZES = [25, 50, 100];

/**
 * The panel of a table's filters, sliding in from the right over a darkened page: a list of boxes
 * per filter, the columns to show and the page size. It only shows the table's state and says what
 * the reader changes; the table keeps the state.
 */
@Component({
  selector: 'dg-filter-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkTrapFocus, IconButton],
  templateUrl: './filter-panel.component.html',
  styleUrl: './filter-panel.component.scss',
})
export class FilterPanel {
  protected readonly t = inject(I18n).t;

  readonly panelId = input.required<string>();
  readonly open = input.required<boolean>();
  readonly filters = input.required<FilterDef[]>();
  /** The options of each filter, by key. */
  readonly options = input.required<Record<string, FilterOption[]>>();
  /** The options ticked per filter, by key. */
  readonly ticked = input.required<Record<string, string[]>>();
  readonly columns = input.required<ColumnDef[]>();
  /** The keys of the columns hidden; the first column is always shown. */
  readonly hidden = input.required<ReadonlySet<string>>();
  readonly pageSize = input.required<number>();

  readonly closed = output<void>();
  readonly ticks = output<{ key: string; option: string; on: boolean }>();
  readonly cleared = output<void>();
  readonly columnShown = output<{ column: ColumnDef; on: boolean }>();
  readonly resized = output<string>();

  protected readonly pageSizes = PAGE_SIZES;
  protected readonly tickedCount = computed(() =>
    Object.values(this.ticked()).reduce((count, values) => count + values.length, 0),
  );
}
