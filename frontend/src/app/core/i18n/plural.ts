/**
 * Plural messages are stored as one key per CLDR plural form: `history.lines.one`, `history.lines.other`, and so on.
 * `I18nService.t('history.lines', { count })` picks the form with `Intl.PluralRules` for the UI language.
 */

/** The base of a plural message: `history.lines` for the keys `history.lines.one` and `history.lines.other`. */
export type PluralBase<K> = K extends `${infer B}.other` ? B : never;

/**
 * A Portuguese dictionary: every key English defines, plus the `many` form Portuguese uses for plural messages
 * (CLDR: a million and up, "1 000 000 de linhas"). English has only `one` and `other`.
 */
export type PortugueseDictionary<K extends string> = Record<K, string> & Record<`${PluralBase<K>}.many`, string>;
