import { CanDeactivateFn } from '@angular/router';

/** A screen that can hold edits the user has not saved. */
export interface HasUnsavedChanges {
  /** True to leave; false or a promise resolving to false to stay. */
  confirmLeave(): boolean | Promise<boolean>;
}

/** Asks the screen before leaving it, so unsaved edits are not lost by accident. */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) => component.confirmLeave();
