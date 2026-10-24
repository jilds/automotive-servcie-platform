import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { Branding } from '../core/api/models';
import { IdentityApi } from '../identity/identity-api.service';
import { Icon } from './icon';
import { LanguageSelect } from './language-select';
import { TenantThemeService } from './theme.service';
import { ThemeToggle } from './theme-toggle';

/**
 * Layout of a workshop's public pages (`/w/{slug}/...`, ADR 0004): the workshop's theme, a header with its logo or
 * name, the language switch and the theme toggle, and the page below. An unknown workshop or a failed load keeps the
 * plain ASP look; the page itself says what went wrong.
 */
@Component({
  selector: 'app-public-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LanguageSelect, ThemeToggle, Icon],
  // Here rather than in the global styles, so only the lazy public routes carry them (initial bundle budget).
  styles: `
    /* The same graphite band as the sign-in page, wider content. */
    .public-page {
      min-height: 100dvh;
      padding-top: clamp(16px, 4vh, 40px);
      background: linear-gradient(180deg, var(--asp-chrome-bg) 0 180px, transparent 180px);
    }

    .public-inner {
      max-width: 960px;
      min-width: 0;
    }
  `,
  template: `
    <main class="public-page px-3 pb-5">
      <div class="public-inner mx-auto">
        <header class="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4" data-bs-theme="dark">
          <span class="app-brand">
            @if (branding()?.logoUrl; as logo) {
              <img class="app-brand-logo" [src]="logo" alt="" />
            } @else {
              <span class="app-brand-mark"><app-icon name="wrench" [size]="20" /></span>
            }
            <span class="app-brand-name text-break">{{ branding()?.name ?? 'ASP' }}</span>
          </span>
          <div class="d-flex align-items-center gap-2"><app-language-select /><app-theme-toggle /></div>
        </header>
        <ng-content />
      </div>
    </main>
  `,
})
export class PublicPage {
  private readonly api = inject(IdentityApi);
  private readonly theme = inject(TenantThemeService);

  /** The workshop's slug, from the route. */
  readonly slug = input.required<string>();

  private readonly current = signal<Branding | null>(null);
  /** The workshop's public theme and name; null while loading or when it could not be loaded. */
  readonly branding = this.current.asReadonly();

  private request: Subscription | null = null;

  constructor() {
    effect(() => this.load(this.slug()));
    inject(DestroyRef).onDestroy(() => {
      this.request?.unsubscribe();
      // The next page (sign-in, the app) sets its own theme; none of this workshop's colours stay behind.
      this.theme.clear();
    });
  }

  private load(slug: string): void {
    this.request?.unsubscribe();
    this.current.set(null);
    this.theme.clear();
    this.request = this.api.brandingBySlug(slug).subscribe({
      next: (b) => {
        this.current.set(b);
        this.theme.apply(b);
      },
      error: () => this.current.set(null),
    });
  }
}
