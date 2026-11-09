/** Shapes of the workshop-floor API (`docs/api/release-4.md`, "Workshop floor"). Timestamps are ISO-8601 UTC instants. */

export interface TimeEntry {
  id: string;
  workOrderId: string;
  lineId: string | null;
  mechanicId: string;
  mechanicName: string | null;
  startedAt: string;
  stoppedAt: string | null;
  /** Whole seconds: stop (or the server's `now` while running) minus start. */
  seconds: number;
  running: boolean;
  autoStopped: boolean;
  editedByName: string | null;
  editedAt: string | null;
  editReason: string | null;
  version: number;
}

export interface LineTime {
  lineId: string;
  seconds: number;
}

export interface TimeSummary {
  /** The server's time when the answer was made; the clock runs as now - startedAt and ticks locally. */
  now: string;
  totalSeconds: number;
  unassignedSeconds: number;
  byLine: LineTime[];
  running: TimeEntry | null;
  /** Newest start first. */
  entries: TimeEntry[];
}

export interface RunningTimer {
  entry: TimeEntry | null;
  workOrderNumber: number | null;
  plateDisplay: string | null;
}

export interface StartTimeRequest {
  lineId: string | null;
}

export interface TimeEntryRequest {
  startedAt: string;
  stoppedAt: string | null;
  lineId: string | null;
  reason: string;
  version: number;
}

export interface MissedTimeRequest {
  mechanicId: string;
  lineId: string | null;
  startedAt: string;
  stoppedAt: string;
  reason: string;
}

export type PhotoStage = 'CHECK_IN' | 'WORK' | 'DELIVERY' | 'DOCUMENTS';
/** Every stage; DOCUMENTS exists for owner and front desk only (`photoStagesFor`). */
export const PHOTO_STAGES: readonly PhotoStage[] = ['CHECK_IN', 'WORK', 'DELIVERY', 'DOCUMENTS'];

export interface Photo {
  id: string;
  workOrderId: string;
  stage: PhotoStage;
  caption: string | null;
  contentType: string;
  sizeBytes: number;
  uploadedByName: string | null;
  uploadedById: string | null;
  createdAt: string;
  version: number;
}

export interface TimeEntryChange {
  id: string;
  oldStartedAt: string;
  oldStoppedAt: string | null;
  oldLineId: string | null;
  changedByName: string | null;
  changedAt: string;
  reason: string;
}

export interface PhotoUpdateRequest {
  stage: PhotoStage;
  caption: string | null;
  version: number;
}

export type ChecklistKind = 'CHECK_IN' | 'DELIVERY';
export const CHECKLIST_KINDS: readonly ChecklistKind[] = ['CHECK_IN', 'DELIVERY'];
export type ChecklistResult = 'OK' | 'ATTENTION' | 'N_A';
export const CHECKLIST_RESULTS: readonly ChecklistResult[] = ['OK', 'ATTENTION', 'N_A'];

export interface ChecklistItem {
  id: string;
  position: number;
  label: string;
  required: boolean;
  result: ChecklistResult | null;
  note: string | null;
  answeredByName: string | null;
  answeredAt: string | null;
}

export interface Checklist {
  kind: ChecklistKind;
  startedAt: string;
  startedByName: string | null;
  requiredTotal: number;
  requiredAnswered: number;
  complete: boolean;
  items: ChecklistItem[];
}

export interface WorkOrderChecklists {
  deliveryChecklistRequired: boolean;
  /** Only the started ones; CHECK_IN first. */
  checklists: Checklist[];
}

export interface AnswerRequest {
  result: ChecklistResult;
  note: string | null;
}

export interface TemplateItem {
  label: string;
  required: boolean;
}

export interface ChecklistTemplate {
  kind: ChecklistKind;
  /** False while the built-in default is shown (the owner has not saved a template). */
  custom: boolean;
  version: number;
  items: TemplateItem[];
}

export interface TemplateRequest {
  items: TemplateItem[];
  version: number;
}
