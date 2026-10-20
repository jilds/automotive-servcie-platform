export type Role = 'OWNER' | 'FRONT_DESK' | 'MECHANIC';

export type WorkOrderStatus =
  | 'DIAGNOSTICS'
  | 'AWAITING_APPROVAL'
  | 'AWAITING_PARTS'
  | 'IN_PROGRESS'
  | 'READY'
  | 'DELIVERED'
  | 'CANCELLED';

export const STATUS_LABELS: Record<WorkOrderStatus, string> = {
  DIAGNOSTICS: 'Diagnostics',
  AWAITING_APPROVAL: 'Awaiting approval',
  AWAITING_PARTS: 'Awaiting parts',
  IN_PROGRESS: 'In progress',
  READY: 'Ready',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

export interface User {
  id: string;
  tenantId: string;
  name: string;
  email: string;
  role: Role;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface Customer {
  id: string;
  name: string;
  nif: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  marketingConsent: boolean;
}

export interface Vehicle {
  id: string;
  customerId: string;
  plate: string;
  vin: string | null;
  make: string | null;
  model: string | null;
  year: number | null;
  fuelType: string | null;
  mileage: number | null;
}

export type LineType = 'LABOR' | 'PART';

export interface WorkOrderLine {
  id: string;
  position: number;
  type: LineType;
  description: string;
  quantity: number;
  unitPriceCents: number | null;
  discountPercent: number | null;
  vatRate: number | null;
  netCents: number | null;
}

export interface WorkOrder {
  id: string;
  number: number;
  vehicleId: string;
  plate: string | null;
  status: WorkOrderStatus;
  allowedNext: WorkOrderStatus[];
  mileageIn: number | null;
  fuelLevel: number | null;
  complaint: string | null;
  damageNotes: string | null;
  assignedMechanicId: string | null;
  statusChangedAt: string;
  lines: WorkOrderLine[];
  netCents: number | null;
  vatCents: number | null;
  totalCents: number | null;
}

export interface BoardCard {
  id: string;
  number: number;
  plate: string | null;
  vehicle: string;
  assignedMechanicId: string | null;
  statusChangedAt: string;
}

export interface Board {
  columns: Partial<Record<WorkOrderStatus, BoardCard[]>>;
}
