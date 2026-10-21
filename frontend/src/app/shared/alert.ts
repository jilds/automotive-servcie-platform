import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Icon, IconName } from './icon';

export type AlertTone = 'danger' | 'warning' | 'success' | 'info';

const ICON: Record<AlertTone, IconName> = {
  danger: 'alert-circle',
  warning: 'alert-triangle',
  success: 'check-circle',
  info: 'info',
};

/**
 * Bootstrap alert with an icon, for example a translated ProblemDetail: `<app-alert>{{ error() }}</app-alert>`.
 * Danger and warning are announced right away (role="alert"); the others politely (role="status").
 */
@Component({
  selector: 'app-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: {
    '[class]': '"alert alert-" + tone() + " d-flex align-items-start gap-2 small"',
    '[attr.role]': 'tone() === "danger" || tone() === "warning" ? "alert" : "status"',
  },
  template: `<app-icon [name]="icon()" /><div class="flex-grow-1 text-break"><ng-content /></div>`,
})
export class Alert {
  readonly tone = input<AlertTone>('danger');
  protected readonly icon = computed(() => ICON[this.tone()]);
}
