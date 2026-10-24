import { DatePipe, DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';

import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/** The full URL of an activation path such as `/activate?token=…`, on this app's address. */
export function absoluteLink(path: string, origin: string): string {
  return origin.replace(/\/$/, '') + path;
}

/**
 * A one-time activation link with a copy button. ASP does not send e-mail yet, so the owner or platform admin
 * hands the link over themselves; it is shown only once (the server keeps only a hash).
 */
@Component({
  selector: 'app-copy-link',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, DatePipe],
  template: `
    <label class="form-label" [for]="id">{{ 'link.label' | t }}</label>
    <div class="input-group">
      <input [id]="id" class="form-control font-monospace small" type="text" readonly [value]="url()" (focus)="$any($event.target).select()" />
      <button type="button" class="btn btn-primary" (click)="copy()">{{ (copied() ? 'link.copied' : 'link.copy') | t }}</button>
    </div>
    <div class="form-text">
      {{ 'link.expires' | t: { date: (expiresAt() | date: 'dd/MM/yyyy HH:mm') } }}
      @if (recipient()) {
        {{ 'link.shareHint' | t: { name: recipient() } }}
      }
    </div>
  `,
})
export class CopyLink {
  private static next = 0;
  private readonly document = inject(DOCUMENT);
  protected readonly i18n = inject(I18nService);
  protected readonly id = `copy-link-${CopyLink.next++}`;

  readonly path = input.required<string>();
  readonly expiresAt = input.required<string>();
  /** Name of the person the link is for, shown in the hint. */
  readonly recipient = input<string | null>(null);

  protected readonly copied = signal(false);
  protected readonly url = computed(() => absoluteLink(this.path(), this.document.location?.origin ?? ''));

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.url());
      this.copied.set(true);
    } catch {
      // No clipboard access (http, old browser): select the text so the user can copy it by hand.
      (this.document.getElementById(this.id) as HTMLInputElement | null)?.select();
    }
  }
}
