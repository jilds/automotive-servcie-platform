// Theme buttons (light / dark / automatic). The choice is stored in localStorage inside try/catch;
// "automatic" removes it so the page follows prefers-color-scheme again. public/theme-init.js reads it on load.
const STORAGE_KEY = 'asp.site.theme';
type Mode = 'light' | 'dark' | 'system';

function stored(): Mode {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === 'light' || value === 'dark' ? value : 'system';
  } catch {
    return 'system';
  }
}

function apply(mode: Mode): void {
  const root = document.documentElement;
  if (mode === 'system') {
    root.removeAttribute('data-theme');
  } else {
    root.setAttribute('data-theme', mode);
  }
  try {
    if (mode === 'system') {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, mode);
    }
  } catch {
    // Storage blocked (private window, settings): the choice lasts for this page only.
  }
  document.querySelectorAll<HTMLButtonElement>('[data-theme-mode]').forEach((button) => {
    button.setAttribute('aria-pressed', String(button.dataset['themeMode'] === mode));
  });
}

function init(): void {
  const mode = stored();
  document.querySelectorAll<HTMLElement>('.theme-toggle').forEach((group) => {
    group.hidden = false;
  });
  document.querySelectorAll<HTMLButtonElement>('[data-theme-mode]').forEach((button) => {
    button.setAttribute('aria-pressed', String(button.dataset['themeMode'] === mode));
    button.addEventListener('click', () => apply((button.dataset['themeMode'] as Mode | undefined) ?? 'system'));
  });
}

init();
