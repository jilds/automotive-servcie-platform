/** Euros typed in a form to integer cents (`45.25` is 4525); empty or not a number gives null. */
export function eurosToCents(euros: number | string | null | undefined): number | null {
  if (euros === null || euros === undefined || euros === '') return null;
  const n = Number(euros);
  return Number.isFinite(n) ? Math.round(Number((n * 100).toPrecision(12))) : null;
}

/** Integer cents as euros for a form field; null stays null. */
export function centsToEuros(cents: number | null | undefined): number | null {
  return cents == null ? null : cents / 100;
}
