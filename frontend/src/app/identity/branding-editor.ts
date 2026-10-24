import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';

import { Branding } from '../core/api/models';
import { isStale } from '../core/api/problem';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Alert } from '../shared/alert';
import { Icon } from '../shared/icon';
import { applyPalette, brandPalette, contrast, parseHex, TenantThemeService, textOn } from '../shared/theme.service';
import { BrandingTarget, IdentityApi } from './identity-api.service';

type ColourKey = 'primaryColor' | 'accentColor';

/** Same limit as the server (`Logo.MAX_BYTES`). */
export const MAX_LOGO_BYTES = 512 * 1024;
export const LOGO_TYPES = 'image/png,image/svg+xml,image/webp';
const SLUG = /^[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?$/;
/** Shown in the colour picker while a colour is "ASP default" (the brand amber and the graphite chrome). */
const DEFAULT_PRIMARY = '#f2b200';

/** Editable copy of a theme; null colours mean "ASP default". */
export interface BrandingDraft {
  displayName: string;
  primaryColor: string | null;
  accentColor: string | null;
  slug: string;
}

export function draftOf(b: Branding): BrandingDraft {
  return { displayName: b.displayName ?? '', primaryColor: b.primaryColor, accentColor: b.accentColor, slug: b.slug };
}

/** Normalises a typed hex value: `0B3D91` or `#0b3d91` become `#0b3d91`; anything else is null. */
export function normaliseHex(value: string): string | null {
  const v = value.trim().toLowerCase();
  const hex = v.startsWith('#') ? v : `#${v}`;
  return parseHex(hex) ? hex : null;
}

/**
 * Appearance card: display name, primary and accent colour (native colour pickers, no extra dependency), logo,
 * and a live preview drawn with the same palette the app applies. The owner edits their own workshop; the
 * platform admin passes a workshop id and may also change the branded login address.
 */
@Component({
  selector: 'app-branding-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, Alert, Icon],
  template: `
    <section class="card shadow-sm" aria-labelledby="branding-title">
      <div class="card-body">
        <h2 class="card-title" id="branding-title">{{ 'branding.title' | t }}</h2>
        <p class="text-body-secondary">{{ 'branding.subtitle' | t }}</p>

        @if (error()) {
          <app-alert>{{ error() }}</app-alert>
        }
        @if (notice()) {
          <app-alert tone="success">{{ notice() }}</app-alert>
        }

        @if (draft(); as d) {
          <div class="row g-4">
            <div class="col-lg-6 vstack gap-3">
              <div>
                <label class="form-label" for="brand-name">{{ 'branding.displayName' | t }}</label>
                <input id="brand-name" class="form-control" maxlength="80" [value]="d.displayName" [placeholder]="saved()?.name ?? ''"
                  (input)="patch({ displayName: $any($event.target).value })" />
                <div class="form-text">{{ 'branding.displayNameHelp' | t }}</div>
              </div>

              @for (c of colourFields; track c.key) {
                <div>
                  <label class="form-label" [for]="'brand-' + c.key">{{ c.label | t }}</label>
                  <div class="d-flex flex-wrap align-items-center gap-2">
                    <input type="color" class="form-control form-control-color" [id]="'brand-' + c.key"
                      [value]="d[c.key] ?? c.fallback()" (input)="setColour(c.key, $any($event.target).value)" />
                    <input class="form-control font-monospace w-auto" size="9" maxlength="7" [attr.aria-label]="'branding.hex' | t"
                      [value]="d[c.key] ?? ''" [placeholder]="'branding.default' | t" (change)="typed(c.key, $any($event.target))" />
                    <button type="button" class="btn btn-sm btn-link" [disabled]="d[c.key] === null" (click)="setColour(c.key, null)">
                      {{ 'branding.useDefault' | t }}
                    </button>
                  </div>
                  <div class="form-text">{{ c.help | t }}</div>
                </div>
              }

              @if (allowSlug()) {
                <div>
                  <label class="form-label" for="brand-slug">{{ 'branding.slug' | t }}</label>
                  <input id="brand-slug" class="form-control font-monospace" maxlength="40" [value]="d.slug" [class.is-invalid]="!slugValid()"
                    (input)="patch({ slug: $any($event.target).value.trim().toLowerCase() })" />
                  @if (!slugValid()) {
                    <div class="invalid-feedback"><app-icon name="alert-circle" [size]="14" /> {{ 'branding.slugInvalid' | t }}</div>
                  } @else {
                    <div class="form-text">{{ 'branding.slugHelp' | t: { url: '/w/' + d.slug } }}</div>
                  }
                </div>
              }

              <div>
                <span class="form-label d-block">{{ 'branding.logo' | t }}</span>
                <div class="d-flex flex-wrap align-items-center gap-2">
                  @if (saved()?.logoUrl; as logo) {
                    <img class="app-brand-logo border rounded p-1 bg-body-tertiary" [src]="logo" [alt]="'branding.logo' | t" />
                  }
                  <input #logoInput type="file" class="visually-hidden" [accept]="logoTypes" (change)="upload($any($event.target))" />
                  <button type="button" class="btn btn-outline-secondary btn-sm" [disabled]="busy()" (click)="logoInput.click()">
                    {{ (saved()?.logoUrl ? 'branding.logoReplace' : 'branding.logoUpload') | t }}
                  </button>
                  @if (saved()?.logoUrl) {
                    <button type="button" class="btn btn-link btn-sm text-danger-emphasis" [disabled]="busy()" (click)="removeLogo()">{{ 'branding.logoRemove' | t }}</button>
                  }
                </div>
                <div class="form-text">{{ 'branding.logoHelp' | t }}</div>
              </div>
            </div>

            <div class="col-lg-6">
              <span class="form-label d-block">{{ 'branding.preview' | t }}</span>
              <div #preview class="brand-preview">
                <div class="brand-preview-chrome p-3" data-bs-theme="dark">
                  <span class="app-brand">
                    @if (saved()?.logoUrl; as logo) {
                      <img class="app-brand-logo" [src]="logo" alt="" />
                    } @else {
                      <span class="app-brand-mark"><app-icon name="wrench" [size]="18" /></span>
                    }
                    <span class="app-brand-name">{{ d.displayName.trim() || saved()?.name }}</span>
                  </span>
                  <nav class="nav nav-pills flex-column app-nav mt-3" aria-hidden="true">
                    <span class="nav-link active"><app-icon name="board" />{{ 'nav.board' | t }}</span>
                    <span class="nav-link"><app-icon name="users" />{{ 'nav.customers' | t }}</span>
                  </nav>
                </div>
                <div class="p-3 vstack gap-2 align-items-start bg-body">
                  <button type="button" class="btn btn-primary" tabindex="-1">{{ 'branding.previewButton' | t }}</button>
                  <a href="#" class="small" tabindex="-1" (click)="$event.preventDefault()">{{ 'branding.previewLink' | t }}</a>
                  <span class="badge bg-primary-subtle text-primary-emphasis">{{ 'role.OWNER' | t }}</span>
                </div>
              </div>
              @if (textInfo(); as info) {
                <p class="form-text mb-0">{{ 'branding.textOn' | t: info }}</p>
              }
            </div>

            <div class="col-12 d-flex flex-wrap justify-content-end gap-2">
              <button type="button" class="btn btn-outline-secondary" [disabled]="!dirty() || busy()" (click)="discard()">{{ 'branding.discard' | t }}</button>
              <button type="button" class="btn btn-primary" [disabled]="!dirty() || busy() || !slugValid()" [attr.aria-busy]="busy()" (click)="save()">
                @if (busy()) {
                  <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                }
                {{ 'branding.save' | t }}
              </button>
            </div>
          </div>
        } @else if (!error()) {
          <div class="placeholder-glow" aria-busy="true"><span class="placeholder col-6"></span></div>
        }
      </div>
    </section>
  `,
})
export class BrandingEditor {
  private readonly api = inject(IdentityApi);
  private readonly i18n = inject(I18nService);
  private readonly theme = inject(TenantThemeService);
  private readonly previewRef = viewChild<ElementRef<HTMLElement>>('preview');

  /** Whose theme: the signed-in owner's (default) or a workshop the platform admin picked. */
  readonly target = input<BrandingTarget>({ kind: 'own' });
  /** Platform admin only: also edit the branded login address. */
  readonly allowSlug = input(false);
  /** The workshop row's new version after a theme save; the same row holds the settings (or the subscription). */
  readonly versionChange = output<number>();

  protected readonly logoTypes = LOGO_TYPES;
  protected readonly saved = signal<Branding | null>(null);
  protected readonly draft = signal<BrandingDraft | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly colourFields = [
    { key: 'primaryColor', label: 'branding.primary', help: 'branding.primaryHelp', fallback: () => DEFAULT_PRIMARY },
    { key: 'accentColor', label: 'branding.accent', help: 'branding.accentHelp', fallback: () => this.draft()?.primaryColor ?? DEFAULT_PRIMARY },
  ] as const;

  protected readonly dirty = computed(() => {
    const s = this.saved();
    const d = this.draft();
    return !!s && !!d && JSON.stringify(draftOf(s)) !== JSON.stringify(d);
  });
  protected readonly slugValid = computed(() => !this.allowSlug() || SLUG.test(this.draft()?.slug ?? ''));
  /** Which text colour primary buttons get, and its contrast, so the owner sees the AA rule at work. */
  protected readonly textInfo = computed(() => {
    const primary = this.draft()?.primaryColor;
    if (!primary) return null;
    const text = textOn(primary);
    return { color: this.i18n.t(text === '#ffffff' ? 'branding.textWhite' : 'branding.textBlack'), ratio: contrast(primary, text).toFixed(1) };
  });

  constructor() {
    effect(() => this.load(this.target()));
    // Live preview: the same palette the app applies, scoped to the preview element.
    effect(() => {
      const element = this.previewRef()?.nativeElement;
      const d = this.draft();
      if (element) applyPalette(element, d ? brandPalette({ primaryColor: d.primaryColor, accentColor: d.accentColor }) : null);
    });
  }

  protected patch(change: Partial<BrandingDraft>): void {
    this.draft.update((d) => (d ? { ...d, ...change } : d));
    this.notice.set(null);
  }

  protected setColour(key: ColourKey, value: string | null): void {
    this.patch(key === 'primaryColor' ? { primaryColor: value } : { accentColor: value });
  }

  protected typed(key: ColourKey, field: HTMLInputElement): void {
    if (!field.value.trim()) {
      this.setColour(key, null);
      return;
    }
    const hex = normaliseHex(field.value);
    if (hex) this.setColour(key, hex);
    else field.value = this.draft()?.[key] ?? '';
  }

  protected discard(): void {
    const s = this.saved();
    if (s) this.draft.set(draftOf(s));
  }

  protected save(): void {
    const d = this.draft();
    if (!d) return;
    const body = {
      displayName: d.displayName.trim() || null,
      primaryColor: d.primaryColor,
      accentColor: d.accentColor,
      version: this.saved()?.version ?? 0,
      ...(this.allowSlug() ? { slug: d.slug } : {}),
    };
    this.run(this.api.saveBranding(this.target(), body), 'branding.saved');
  }

  /** Another form on the page saved the same workshop row: the next theme save must send its new version. */
  useVersion(version: number): void {
    this.saved.update((s) => (s ? { ...s, version } : s));
  }

  protected upload(field: HTMLInputElement): void {
    const file = field.files?.[0];
    field.value = '';
    if (!file) return;
    if (file.size > MAX_LOGO_BYTES) {
      this.error.set(this.i18n.t('error.logo-too-large'));
      return;
    }
    this.run(this.api.uploadLogo(this.target(), file), 'branding.logoSaved');
  }

  protected removeLogo(): void {
    this.run(this.api.removeLogo(this.target()), 'branding.logoRemoved');
  }

  private run(request: ReturnType<IdentityApi['saveBranding']>, done: 'branding.saved' | 'branding.logoSaved' | 'branding.logoRemoved'): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    const keep = this.draft();
    request.subscribe({
      next: (b) => {
        this.saved.set(b);
        // A logo change keeps unsaved colour edits; a save takes the server's values.
        this.draft.set(done === 'branding.saved' || !keep ? draftOf(b) : { ...keep, slug: b.slug });
        this.busy.set(false);
        this.notice.set(this.i18n.t(done));
        if (this.target().kind === 'own') this.theme.apply(b);
        if (done === 'branding.saved') this.versionChange.emit(b.version);
      },
      error: (e) => {
        this.busy.set(false);
        if (isStale(e)) {
          // Saved elsewhere since it loaded: show the stored theme, then say why.
          this.load(this.target(), this.i18n.error(e));
          return;
        }
        this.error.set(this.i18n.error(e));
      },
    });
  }

  private load(target: BrandingTarget, message: string | null = null): void {
    this.error.set(null);
    this.api.branding(target).subscribe({
      next: (b) => {
        this.saved.set(b);
        this.draft.set(draftOf(b));
        this.error.set(message);
      },
      error: (e) => this.error.set(this.i18n.error(e)),
    });
  }
}
