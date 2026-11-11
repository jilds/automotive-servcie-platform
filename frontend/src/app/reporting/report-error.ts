import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';

/**
 * The error of a report load. A busy server (429 `try-again`) is a retryable warning with a "Try again" button; the
 * data already on screen stays. Anything else is the translated problem.
 */
@Component({
  selector: 'app-report-error',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alert, TranslatePipe],
  template: `
    @if (busy()) {
      <app-alert tone="warning">
        <span class="d-flex flex-wrap align-items-center gap-2" data-test="busy">
          <span>{{ 'reports.busy' | t }}</span>
          <button type="button" class="btn btn-sm btn-outline-secondary" data-test="retry" (click)="retry.emit()">{{ 'reports.retry' | t }}</button>
        </span>
      </app-alert>
    } @else if (message()) {
      <app-alert><span data-test="error">{{ message() }}</span></app-alert>
    }
  `,
})
export class ReportError {
  readonly message = input<string | null>(null);
  readonly busy = input(false);
  readonly retry = output();
}
