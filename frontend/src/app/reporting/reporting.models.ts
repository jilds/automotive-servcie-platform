/** Shapes of `GET /api/v1/reports/*` (docs/api/release-4.md, "Reports"). Money in cents before VAT unless named `Total` or `Vat`. */

export type MarginBasis = 'PARTS_AND_LABOUR' | 'PARTS_ONLY';

export const REPORT_KINDS = ['cost-vs-quote', 'productivity', 'kpis'] as const;
export type ReportKind = (typeof REPORT_KINDS)[number];

/** The period as sent: local dates `yyyy-MM-dd` in the workshop's time zone, both inclusive. Left out = the server default. */
export interface PeriodQuery {
  from?: string;
  to?: string;
}

export interface CostVsQuoteQuery extends PeriodQuery {
  mechanicId?: string;
  /** `true` only orders with an approved quote, `false` only without one, left out all. */
  hasApprovedQuote?: boolean;
  page?: number;
  size?: number;
}

export interface ProductivityQuery extends PeriodQuery {
  mechanicId?: string;
}

export interface ApprovedQuote {
  id: string;
  number: number;
  approvedAt: string;
  channel: 'CUSTOMER' | 'STAFF';
  netCents: number;
  totalCents: number;
}

export interface CostVsQuoteItem {
  workOrderId: string;
  number: number;
  plate: string;
  deliveredAt: string;
  mechanicId: string | null;
  mechanicName: string | null;
  quote: ApprovedQuote | null;
  finalNetCents: number;
  finalVatCents: number;
  finalTotalCents: number;
  differenceCents: number | null;
  differencePercent: number | null;
  standardHours: number;
  trackedHours: number;
  partsCostCents: number;
  labourCostCents: number | null;
  marginCents: number;
  marginPercent: number | null;
}

export interface CostVsQuoteSummary {
  orders: number;
  ordersWithApprovedQuote: number;
  quoteTotalCents: number;
  finalTotalCents: number;
  differenceCents: number;
  differencePercent: number | null;
  standardHours: number;
  trackedHours: number;
  netCents: number;
  partsCostCents: number;
  labourCostCents: number | null;
  marginCents: number;
  marginPercent: number | null;
}

export interface CostVsQuoteReport {
  from: string;
  to: string;
  timezone: string;
  labourCostSet: boolean;
  marginBasis: MarginBasis;
  summary: CostVsQuoteSummary;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  items: CostVsQuoteItem[];
}

export interface ProductivityRow {
  trackedHours: number;
  jobs: number;
  standardHours: number;
  trackedHoursOnJobs: number;
  efficiency: number | null;
  averageHoursInWorkshop: number | null;
}

export interface MechanicProductivity extends ProductivityRow {
  mechanicId: string;
  name: string;
  active: boolean;
}

export interface ProductivityReport {
  from: string;
  to: string;
  timezone: string;
  mechanics: MechanicProductivity[];
  totals: ProductivityRow;
}

/** A KPI whose data does not exist yet: no numbers, only the feature that will bring them. */
export interface KpiUnavailable {
  available: false;
  availableWith: string;
}

export type Kpi<T> = (T & { available: true }) | KpiUnavailable;

export interface AverageTimePerJob {
  trackedHours: number;
  ordersWithTime: number;
  hoursPerJob: number | null;
  byService: { serviceItemId: string; name: string; trackedHours: number; orders: number; hoursPerJob: number | null }[];
}

export interface AverageOrderValue {
  source: string;
  invoicedOrders: number;
  creditedOrders: number;
  netCents: number;
  totalCents: number;
  averageNetCents: number | null;
  averageTotalCents: number | null;
  notYetInvoiced: { orders: number; netCents: number; totalCents: number };
}

export interface MarginKpi {
  basis: MarginBasis;
  labourCostSet: boolean;
  orders: number;
  netCents: number;
  partsCostCents: number;
  labourCostCents: number | null;
  marginCents: number;
  marginPercent: number | null;
  /** The package parts inside the order margin (not additional): lines that came from a catalogue package. */
  packageParts: { lines: number; netCents: number; partsCostCents: number; marginCents: number; marginPercent: number | null };
  /** Every other part (from stock, typed, or package parts of orders from before V23); also inside the order margin. */
  otherParts: { netCents: number; partsCostCents: number; marginCents: number; marginPercent: number | null };
  byService: {
    serviceItemId: string;
    name: string;
    lines: number;
    orders: number;
    netCents: number;
    trackedHours: number;
    labourCostCents: number | null;
    marginCents: number;
    marginPercent: number | null;
  }[];
}

export interface RetentionKpi {
  months: number;
  customers: number;
  retained: number;
  ratePercent: number | null;
  /** False while the follow-up window is not over: the rate still reads low (provisional). */
  final: boolean;
  /** How many of the `months` have passed since the period end, 0..months. */
  monthsCovered: number;
}

export interface ComebacksKpi {
  days: number;
  orders: number;
  ratePercent: number | null;
}

/** Whatever a future KPI will carry once its feature exists; shown as plain figures. */
export type FutureKpiFigures = Record<string, unknown>;

export interface KpiReport {
  from: string;
  to: string;
  timezone: string;
  labourCostSet: boolean;
  retentionMonths: number;
  deliveredOrders: number;
  averageTimePerJob: Kpi<AverageTimePerJob>;
  averageOrderValue: Kpi<AverageOrderValue>;
  margin: Kpi<MarginKpi>;
  retention: Kpi<RetentionKpi>;
  comebacks: Kpi<ComebacksKpi>;
  occupancy: Kpi<FutureKpiFigures>;
  satisfaction: Kpi<FutureKpiFigures>;
  complaints: Kpi<FutureKpiFigures>;
}
