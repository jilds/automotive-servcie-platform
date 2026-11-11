/**
 * English report texts shown outside the report routes: the error codes (the export button and the shell show them
 * through `I18nService.error`). Eager: spread into `messages.en.ts`, so keep it small.
 */
export const reportsCoreEn = {
  'error.report-range-too-large': 'The period is longer than 24 months. Choose a shorter period.',
  'error.report-too-many-rows': 'Too many orders in this period for one report. Choose a shorter period.',
} as const;

export type ReportsCoreKey = keyof typeof reportsCoreEn;
