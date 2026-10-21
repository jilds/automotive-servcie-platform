import { DOCUMENT } from '@angular/common';
import { computed, effect, inject, Injectable, signal } from '@angular/core';

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
