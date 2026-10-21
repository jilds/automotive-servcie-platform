import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Icon, IconName } from './icon';
import { ThemeMode, ThemeService } from './theme.service';

/** Three-way switch for the colour theme: light, dark or follow the system. */
@Component({
  selector: 'app-theme-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, TranslatePipe],
  template: `
    <div class="btn-group btn-group-sm" role="group" [attr.aria-label]="'theme.label' | t">
      @for (o of options; track o.mode) {
        <button
          type="button"
          class="btn btn-icon"
          [class.btn-primary]="theme.mode() === o.mode"
          [class.btn-outline-secondary]="theme.mode() !== o.mode"
          [attr.aria-pressed]="theme.mode() === o.mode"
          [attr.aria-label]="o.key | t"
          [title]="o.key | t"
          (click)="theme.use(o.mode)"
        >
          <app-icon [name]="o.icon" [size]="18" />
        </button>
      }
    </div>
  `,
})
export class ThemeToggle {
  protected readonly theme = inject(ThemeService);
  protected readonly options: readonly { mode: ThemeMode; icon: IconName; key: `theme.${ThemeMode}` }[] = [
    { mode: 'light', icon: 'sun', key: 'theme.light' },
    { mode: 'dark', icon: 'moon', key: 'theme.dark' },
    { mode: 'system', icon: 'monitor', key: 'theme.system' },
  ];
}
