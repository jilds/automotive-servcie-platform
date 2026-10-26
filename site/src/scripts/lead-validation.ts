// Client checks of the proposal form. They mirror LeadRequest on the server
// (docs/api/release-3.md, "POST /api/v1/public-site/leads"); the server stays the authority.

export const VEHICLE_TYPES = ['CAR', 'MOTORCYCLE', 'BOTH'] as const;
export type VehicleType = (typeof VEHICLE_TYPES)[number];

export const LEAD_FIELDS = [
  'name',
  'workshopName',
  'phone',
  'email',
  'district',
  'vehicleType',
  'message',
  'privacyAccepted',
] as const;
export type LeadField = (typeof LEAD_FIELDS)[number];

/** Which translated message a field shows (form.error.<rule>). */
export type FieldRule =
  | 'required'
  | 'name'
  | 'district'
  | 'phone'
  | 'email'
  | 'contact'
  | 'vehicleType'
  | 'message'
  | 'privacy';
export type LeadErrors = Partial<Record<LeadField, FieldRule>>;

export interface LeadInput {
  name: string;
  workshopName: string;
  phone: string;
  email: string;
  district: string;
  vehicleType: string;
  message: string;
  privacyAccepted: boolean;
  marketingConsent: boolean;
  website: string;
}

/** The request body, exactly the fields the endpoint takes. */
export interface LeadPayload {
  name: string;
  workshopName: string;
  phone: string;
  email: string;
  district: string;
  vehicleType: string;
  message: string;
  privacyAccepted: boolean;
  marketingConsent: boolean;
  website: string;
}

const MAX_NAME = 120;
const MAX_DISTRICT = 60;
const MAX_PHONE = 30;
const MAX_EMAIL = 254;
const MAX_MESSAGE = 2000;

// Same as the server's ONE_LINE_NO_LINK: no "://", no "www." (any case), no control characters (C0, C1),
// no line or paragraph separators, no < or >.
const CONTROL_OR_ANGLE = /[\u0000-\u001f\u007f-\u009f\u2028\u2029<>]/;
const LINK = /:\/\/|www\./i;
const PHONE = /^[+0-9 ()-]*$/;
const EMAIL = /^[^\s@]+@[^\s@]+$/;

export function isOneLineNoLink(value: string): boolean {
  return !CONTROL_OR_ANGLE.test(value) && !LINK.test(value);
}

export function toPayload(input: LeadInput): LeadPayload {
  return {
    name: input.name.trim(),
    workshopName: input.workshopName.trim(),
    phone: input.phone.trim(),
    email: input.email.trim(),
    district: input.district.trim(),
    vehicleType: input.vehicleType,
    message: input.message.trim(),
    privacyAccepted: input.privacyAccepted,
    marketingConsent: input.marketingConsent,
    website: input.website,
  };
}

function checkName(value: string): FieldRule | undefined {
  if (value === '') return 'required';
  if (value.length > MAX_NAME || !isOneLineNoLink(value)) return 'name';
  return undefined;
}

export function validate(payload: LeadPayload): LeadErrors {
  const errors: LeadErrors = {};
  const name = checkName(payload.name);
  if (name) errors.name = name;
  const workshopName = checkName(payload.workshopName);
  if (workshopName) errors.workshopName = workshopName;

  if (payload.phone.length > MAX_PHONE || !PHONE.test(payload.phone)) errors.phone = 'phone';
  if (payload.email !== '' && (payload.email.length > MAX_EMAIL || !EMAIL.test(payload.email))) errors.email = 'email';
  if (payload.phone === '' && payload.email === '') {
    errors.phone = 'contact';
    errors.email = 'contact';
  }

  if (payload.district.length > MAX_DISTRICT || !isOneLineNoLink(payload.district)) errors.district = 'district';
  if (!(VEHICLE_TYPES as readonly string[]).includes(payload.vehicleType)) errors.vehicleType = 'vehicleType';
  if (payload.message.length > MAX_MESSAGE) errors.message = 'message';
  if (!payload.privacyAccepted) errors.privacyAccepted = 'privacy';
  return errors;
}

/** Maps the `errors` of a 400 `validation` answer to the form's own translated rules. Unknown keys are dropped. */
export function fromServerErrors(serverErrors: Record<string, unknown>, payload: LeadPayload): LeadErrors {
  const errors: LeadErrors = {};
  const noContact = payload.phone === '' && payload.email === '';
  for (const key of Object.keys(serverErrors)) {
    switch (key) {
      case 'name':
      case 'workshopName':
        errors[key] = payload[key] === '' ? 'required' : 'name';
        break;
      case 'phone':
      case 'email':
        errors[key] = noContact ? 'contact' : key;
        break;
      case 'district':
        errors.district = 'district';
        break;
      case 'vehicleType':
        errors.vehicleType = 'vehicleType';
        break;
      case 'message':
        errors.message = 'message';
        break;
      case 'privacyAccepted':
        errors.privacyAccepted = 'privacy';
        break;
      default:
        break;
    }
  }
  return errors;
}

/** Minutes to wait from a Retry-After header (seconds or an HTTP date); undefined when missing or unreadable. */
export function retryAfterMinutes(header: string | null, now: number = Date.now()): number | undefined {
  if (header === null || header.trim() === '') return undefined;
  const trimmed = header.trim();
  let seconds: number;
  if (/^\d+$/.test(trimmed)) {
    seconds = Number(trimmed);
  } else {
    const date = Date.parse(trimmed);
    if (Number.isNaN(date)) return undefined;
    seconds = (date - now) / 1000;
  }
  return Math.max(1, Math.ceil(seconds / 60));
}
