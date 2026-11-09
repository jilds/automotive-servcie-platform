/**
 * English workshop-floor texts shown outside the work order route: the running-timer banner (every screen of a
 * mechanic) and the error codes. Eager: spread into `messages.en.ts`, so keep it small (initial bundle budget).
 */
export const floorCoreEn = {
  'floor.banner.running': 'Timer running on #{number} {plate}',
  'floor.banner.open': 'Open job #{number}, the timer is running',
  'error.timer-not-running': 'No timer is running on this job.',
  'error.time-tracking-closed': 'This job is ready or closed, so a timer cannot start.',
  'error.no-mechanic-assigned': 'Give the job to a mechanic before starting a timer.',
  'error.time-entry-not-found': 'Time entry not found.',
  'error.invalid-time-range': 'Times cannot be in the future, and the end cannot be before the start.',
  'error.photo-not-found': 'Photo not found.',
  'error.photo-too-large': 'This photo is over 5 MB.',
  'error.photo-type-not-allowed': 'Only JPEG, PNG and WebP pictures are accepted.',
  'error.photo-limit-reached': 'This job already has 30 photos. Delete one to add another.',
  'error.checklist-incomplete': 'The car cannot be delivered yet: the required items of the delivery checklist are not all answered.',
  'error.checklist-not-found': 'This checklist has not been started.',
  'error.checklist-item-not-found': 'Checklist item not found.',
  'error.photo-storage-full': 'This workshop has used all of its photo storage. Delete photos you no longer need or ask the platform administrator.',
  'error.time-entry-overlap': 'This time overlaps another entry of the same mechanic. Adjust the start or the end.',
  'error.upload-too-large': 'This file is over the 5 MB upload limit.',
} as const;

export type FloorCoreKey = keyof typeof floorCoreEn;
