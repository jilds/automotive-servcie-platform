import { DOCUMENT } from '@angular/common';
import { computed, effect, inject, Injectable, signal } from '@angular/core';

import { Branding } from '../core/api/models';

export type ThemeMode = 'light' | 'dark' | 'system';

export const THEME_MODES: readonly ThemeMode[] = ['light', 'dark', 'system'];

/** Must match the inline script in index.html that applies the theme before Angular starts. */
const STORAGE_KEY = 'asp.theme';

const DARK_QUERY = '(prefers-color-scheme: dark)';

/**
 * Holds the colour theme. "system" follows prefers-color-scheme (and its changes); light and dark are explicit.
 * The resolved theme is set as data-bs-theme on <html>, Bootstrap's colour-mode attribute. Remembered in this browser.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  readonly mode = signal<ThemeMode>(readStoredMode());
  private readonly systemDark = signal(false);
  /** The theme in effect: the explicit choice, or the system preference in "system" mode. */
  readonly resolved = computed<'light' | 'dark'>(() => {
    const mode = this.mode();
    if (mode !== 'system') return mode;
    return this.systemDark() ? 'dark' : 'light';
  });

  constructor() {
    const media = this.document.defaultView?.matchMedia?.(DARK_QUERY);
    if (media) {
      this.systemDark.set(media.matches);
      media.addEventListener?.('change', (e) => this.systemDark.set(e.matches));
    }
    effect(() => this.document.documentElement.setAttribute('data-bs-theme', this.resolved()));
    effect(() => {
      const mode = this.mode();
      try {
        localStorage.setItem(STORAGE_KEY, mode);
      } catch {
        // Storage can be blocked (private window); the choice then lasts for this visit only.
      }
    });
  }

  use(mode: ThemeMode): void {
    this.mode.set(mode);
  }
}

function readStoredMode(): ThemeMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY) as ThemeMode | null;
    if (stored && THEME_MODES.includes(stored)) return stored;
  } catch {
    // Fall through to the default.
  }
  return 'system';
}

// ---------- Workshop theme (Release 3, ADR 0003) ----------

/** The colours of a workshop theme as the API returns them; null means "ASP default". */
export interface BrandColors {
  primaryColor: string | null;
  /** From the API: `#ffffff` or `#000000`, whichever reaches 4.5:1 on the primary colour. Computed when absent. */
  primaryTextColor?: string | null;
  accentColor: string | null;
}

type Rgb = readonly [number, number, number];

/** Backgrounds the derived colours are drawn on (see `_theme.scss` and `_tokens.scss`). */
const LIGHT_BACKGROUNDS = ['#ffffff', '#f3f4f6', '#f7f8fa'];
const DARK_BACKGROUNDS = ['#17191c', '#1f2226', '#25292e'];
/** The sidebar and top bar are graphite in both modes. */
const CHROME_BACKGROUNDS = ['#1c1f23', '#141619'];
/** WCAG 2.2 AA: 4.5:1 for text (1.4.3), 3:1 for marks and focus outlines (1.4.11). */
const TEXT_CONTRAST = 4.5;
const UI_CONTRAST = 3;

export function parseHex(hex: string): Rgb | null {
  const m = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(hex.trim());
  return m ? [parseInt(m[1], 16), parseInt(m[2], 16), parseInt(m[3], 16)] : null;
}

function toHex(rgb: readonly number[]): string {
  return '#' + rgb.map((c) => Math.round(Math.min(255, Math.max(0, c))).toString(16).padStart(2, '0')).join('');
}

function luminance([r, g, b]: Rgb): number {
  const channel = (c: number) => {
    const s = c / 255;
    return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

/** WCAG contrast ratio of two `#rrggbb` colours, 1 to 21. */
export function contrast(a: string, b: string): number {
  const la = luminance(parseHex(a) ?? [0, 0, 0]);
  const lb = luminance(parseHex(b) ?? [0, 0, 0]);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/** `weight` of `a` mixed into `b` (0..1), like Sass `mix`. */
function mix(a: string, b: string, weight: number): string {
  const x = parseHex(a)!;
  const y = parseHex(b)!;
  return toHex([0, 1, 2].map((i) => x[i] * weight + y[i] * (1 - weight)));
}

/** Text colour for a fill: white when it reaches 4.5:1, else black (the server's rule too, see ADR 0003). */
export function textOn(fill: string): string {
  return contrast(fill, '#ffffff') >= TEXT_CONTRAST ? '#ffffff' : '#000000';
}

/** Darkens (towards black) or lightens (towards white) a colour in 5% steps until it reaches `ratio` on every background. */
export function ensureContrast(color: string, backgrounds: readonly string[], ratio: number, towards: '#000000' | '#ffffff'): string {
  let result = color;
  for (let step = 1; step <= 20 && backgrounds.some((bg) => contrast(result, bg) < ratio); step++) {
    result = mix(towards, color, step / 20);
  }
  return result;
}

function rgbList(hex: string): string {
  return parseHex(hex)!.join(', ');
}

/**
 * The CSS custom properties (`--asp-brand-*`) of a workshop theme, read by `styles/_tenant-theme.scss` under
 * `[data-tenant-theme]`. Every derived colour is checked against the backgrounds it is drawn on: links and tinted
 * text reach 4.5:1 in light and dark mode, active marks 3:1 on the graphite chrome. Null when nothing is customised.
 */
export function brandPalette(colors: BrandColors): Record<string, string> | null {
  const primary = colors.primaryColor && parseHex(colors.primaryColor) ? colors.primaryColor.toLowerCase() : null;
  const accentIn = colors.accentColor && parseHex(colors.accentColor) ? colors.accentColor.toLowerCase() : null;
  if (!primary && !accentIn) return null;
  const vars: Record<string, string> = {};
  if (primary) {
    const onPrimary = colors.primaryTextColor && parseHex(colors.primaryTextColor) ? colors.primaryTextColor : textOn(primary);
    // Like Bootstrap: darker on hover under white text, lighter under black text, so the text keeps its contrast.
    const towardsText = onPrimary === '#ffffff' ? '#000000' : '#ffffff';
    const link = ensureContrast(primary, LIGHT_BACKGROUNDS, TEXT_CONTRAST, '#000000');
    const linkHover = mix('#000000', link, 0.2);
    const linkDark = ensureContrast(primary, DARK_BACKGROUNDS, TEXT_CONTRAST, '#ffffff');
    const linkDarkHover = mix('#ffffff', linkDark, 0.2);
    const subtleBg = mix(primary, '#ffffff', 0.14);
    const subtleBgDark = mix(primary, '#17191c', 0.22);
    Object.assign(vars, {
      primary,
      'primary-rgb': rgbList(primary),
      'on-primary': onPrimary,
      'primary-hover': mix(towardsText, primary, 0.15),
      'primary-active': mix(towardsText, primary, 0.2),
      link,
      'link-rgb': rgbList(link),
      'link-hover': linkHover,
      'link-hover-rgb': rgbList(linkHover),
      'link-dark': linkDark,
      'link-dark-rgb': rgbList(linkDark),
      'link-dark-hover': linkDarkHover,
      'link-dark-hover-rgb': rgbList(linkDarkHover),
      'subtle-bg': subtleBg,
      'subtle-border': mix(primary, '#ffffff', 0.4),
      emphasis: ensureContrast(primary, [subtleBg, ...LIGHT_BACKGROUNDS], TEXT_CONTRAST, '#000000'),
      'subtle-bg-dark': subtleBgDark,
      'subtle-border-dark': mix(primary, '#17191c', 0.45),
      'emphasis-dark': ensureContrast(primary, [subtleBgDark, ...DARK_BACKGROUNDS], TEXT_CONTRAST, '#ffffff'),
      'focus-ring': `rgba(${rgbList(link)}, 0.25)`,
      'focus-ring-dark': `rgba(${rgbList(linkDark)}, 0.3)`,
    });
  }
  const accent = accentIn ?? primary!;
  Object.assign(vars, {
    accent,
    'on-accent': textOn(accent),
    'accent-chrome': ensureContrast(accent, CHROME_BACKGROUNDS, UI_CONTRAST, '#ffffff'),
  });
  return Object.fromEntries(Object.entries(vars).map(([k, v]) => [`--asp-brand-${k}`, v]));
}

/** Marks an element as themed (`data-tenant-theme`) and sets the palette on it; with no palette, removes both. */
export function applyPalette(element: HTMLElement, palette: Record<string, string> | null): void {
  for (const name of Array.from(element.style)) {
    if (name.startsWith('--asp-brand-')) element.style.removeProperty(name);
  }
  if (!palette) {
    element.removeAttribute('data-tenant-theme');
    return;
  }
  for (const [name, value] of Object.entries(palette)) element.style.setProperty(name, value);
  element.setAttribute('data-tenant-theme', '');
}

/**
 * The workshop theme in effect: applied to `<html>` after login (shell) and on branded login pages (`/w/{slug}`),
 * cleared on the plain login page and in the platform admin console. Bootstrap compiles `.btn-primary`, `.nav` and
 * friends from Sass, so `_tenant-theme.scss` redefines their variables under `[data-tenant-theme]` in light and dark
 * mode; without a theme the ASP default applies.
 */
@Injectable({ providedIn: 'root' })
export class TenantThemeService {
  private readonly document = inject(DOCUMENT);
  /** The branding shown in the sidebar and on branded pages; null for the ASP default. */
  readonly branding = signal<Branding | null>(null);

  apply(branding: Branding | null): void {
    this.branding.set(branding);
    applyPalette(this.document.documentElement, branding ? brandPalette(branding) : null);
  }

  clear(): void {
    this.apply(null);
  }
}
