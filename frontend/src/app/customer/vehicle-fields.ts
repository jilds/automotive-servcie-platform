import { MessageKey } from '../core/i18n/messages.en';

/** Fuel types as stored on the vehicle; the labels are translated. */
export const FUELS = ['Gasoline', 'Diesel', 'Hybrid', 'Electric', 'LPG'] as const;

export interface FuelOption {
  value: string;
  /** Translation key, or null for a stored value outside the list, shown as is. */
  key: MessageKey | null;
}

/** The fuel select options; a stored free-text value that is not in the list is kept as an extra option. */
export function fuelOptions(current?: string | null): FuelOption[] {
  const options: FuelOption[] = FUELS.map((value) => ({ value, key: `fuel.${value}` }));
  if (current && !(FUELS as readonly string[]).includes(current)) options.push({ value: current, key: null });
  return options;
}

/** Year limits enforced by the API (1950..2100). */
export const YEAR_MIN = 1950;
export const YEAR_MAX = 2100;
export const VIN_LENGTH = 17;
