import { beforeEach } from 'vitest';

/**
 * Runs before every unit test (`setupFiles` in angular.json). Test files share one environment per worker (no
 * isolation), so a test that switches the language, theme or session would otherwise hand its choice to whatever
 * runs next in that worker, and the result would depend on how many CPUs split the files (CI has fewer than a
 * workstation). Each test starts from empty storage, as a first visit does: Portuguese (Portugal), signed out.
 */
beforeEach(() => {
  try {
    localStorage.clear();
    sessionStorage.clear();
  } catch {
    // No storage in this environment: nothing to leak.
  }
});
