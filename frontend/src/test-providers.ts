import { Provider } from '@angular/core';

import { ALL_TEXTS } from './app/core/i18n/all-texts';
import { PRELOADED_TEXTS } from './app/core/i18n/feature-texts';

/**
 * Providers for every unit test (`providersFile` in angular.json): all dictionaries preloaded, so components show
 * their texts and a language switch applies at once. The lazy loading itself is tested with an empty preload.
 */
export default [{ provide: PRELOADED_TEXTS, useValue: ALL_TEXTS }] satisfies Provider[];
