import { AccountRole, WorkOrderStatus } from '../core/api/models';
import { Checklist, ChecklistKind, ChecklistResult, PHOTO_STAGES, Photo, PhotoStage, TimeEntry } from './floor.models';

/** Who is acting: the signed-in role and user id (null when nobody). */
export interface Actor {
  role: AccountRole | null;
  userId: string | null;
}

/** The part of a work order the floor rules look at. */
export interface FloorOrder {
  status: WorkOrderStatus;
  assignedMechanicId: string | null;
}

const isFrontOffice = (a: Actor) => a.role === 'OWNER' || a.role === 'FRONT_DESK';
const isAssignedMechanic = (a: Actor, w: FloorOrder) => a.role === 'MECHANIC' && !!a.userId && a.userId === w.assignedMechanicId;

// ---------------------------------------------------------------- time

/** Statuses in which a timer can no longer be started (backend `time-tracking-closed`). */
const TRACKING_CLOSED: readonly WorkOrderStatus[] = ['READY', 'DELIVERED', 'CANCELLED'];

export type TimerBlock = 'closed' | 'no-mechanic' | 'not-assigned' | 'front-desk' | null;

/**
 * Whether this user may start and stop the order's timer, and if not why. The assigned mechanic works for themselves,
 * the owner for the assigned mechanic; the front desk only corrects. Mirrors the backend rules.
 */
export function timerAccess(actor: Actor, order: FloorOrder): { allowed: boolean; block: TimerBlock } {
  if (actor.role === 'FRONT_DESK') return { allowed: false, block: 'front-desk' };
  if (actor.role === 'MECHANIC') {
    if (!isAssignedMechanic(actor, order)) return { allowed: false, block: 'not-assigned' };
  } else if (actor.role === 'OWNER') {
    if (!order.assignedMechanicId) return { allowed: false, block: 'no-mechanic' };
  } else {
    return { allowed: false, block: 'not-assigned' };
  }
  if (TRACKING_CLOSED.includes(order.status)) return { allowed: false, block: 'closed' };
  return { allowed: true, block: null };
}

/** A running timer can always be stopped by those who may start it (the order auto-stops it when it closes). */
export function canStopTimer(actor: Actor, order: FloorOrder): boolean {
  return actor.role === 'OWNER' ? !!order.assignedMechanicId : isAssignedMechanic(actor, order);
}

/** Owner and front desk correct entries and add missed time. */
export function canCorrectTime(actor: Actor): boolean {
  return isFrontOffice(actor);
}

/** Milliseconds to add to the local clock to get the server clock, from the `now` of an answer. */
export function clockOffset(serverNowIso: string, localNowMs: number): number {
  const server = Date.parse(serverNowIso);
  return Number.isNaN(server) ? 0 : server - localNowMs;
}

/** Whole seconds a running entry has run at `nowMs` (server clock), never below 0. */
export function elapsedSeconds(startedAtIso: string, nowMs: number): number {
  const started = Date.parse(startedAtIso);
  if (Number.isNaN(started)) return 0;
  return Math.max(0, Math.floor((nowMs - started) / 1000));
}

/**
 * A total that includes a running entry grows with the clock: the server total counted it up to `serverNowMs`, so the
 * seconds since then are added while something runs.
 */
export function liveTotal(totalSeconds: number, running: boolean, serverNowMs: number, nowMs: number): number {
  return running ? totalSeconds + Math.max(0, Math.floor((nowMs - serverNowMs) / 1000)) : totalSeconds;
}

/** `H:MM:SS` for a duration in seconds (hours are not capped). */
export function clock(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const rest = s % 60;
  return `${h}:${String(m).padStart(2, '0')}:${String(rest).padStart(2, '0')}`;
}

/** Seconds of an entry for display: a running one counts up to the clock, a stopped one is as stored. */
export function entrySeconds(e: Pick<TimeEntry, 'running' | 'startedAt' | 'seconds'>, nowMs: number): number {
  return e.running ? elapsedSeconds(e.startedAt, nowMs) : e.seconds;
}

const pad = (n: number) => String(n).padStart(2, '0');

/** An instant as the local `YYYY-MM-DDTHH:mm` a `datetime-local` input takes. */
export function toLocalInput(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * The instant of a `datetime-local` value. When the value is the same minute as the original instant, the original
 * (with its seconds) is kept, so a correction of another field does not move the time.
 */
export function fromLocalInput(value: string, original?: string | null): string | null {
  if (!value) return null;
  if (original && toLocalInput(original) === value) return original;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

export type RangeProblem = 'start-required' | 'stop-required' | 'future' | 'order' | null;

/** Client-side mirror of `invalid-time-range`: nothing in the future, stop not before start. */
export function rangeProblem(startedAt: string | null, stoppedAt: string | null, stopRequired: boolean, nowMs: number): { field: 'startedAt' | 'stoppedAt'; problem: Exclude<RangeProblem, null> } | null {
  if (!startedAt) return { field: 'startedAt', problem: 'start-required' };
  const start = Date.parse(startedAt);
  if (start > nowMs) return { field: 'startedAt', problem: 'future' };
  if (!stoppedAt) return stopRequired ? { field: 'stoppedAt', problem: 'stop-required' } : null;
  const stop = Date.parse(stoppedAt);
  if (stop > nowMs) return { field: 'stoppedAt', problem: 'future' };
  if (stop < start) return { field: 'stoppedAt', problem: 'order' };
  return null;
}

/** Seconds of a line, 0 when it has no time. */
export function lineSeconds(byLine: readonly { lineId: string; seconds: number }[], lineId: string): number {
  return byLine.find((l) => l.lineId === lineId)?.seconds ?? 0;
}

// ---------------------------------------------------------------- photos

export const MAX_PHOTOS = 30;
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
export const PHOTO_MAX_EDGE = 1600;
export const CAPTION_MAX = 200;

export const photosLeft = (count: number) => Math.max(0, MAX_PHOTOS - count);

/** Upload: owner, front desk and the assigned mechanic. */
export function canUploadPhoto(actor: Actor, order: FloorOrder): boolean {
  return isFrontOffice(actor) || isAssignedMechanic(actor, order);
}

/** Change or delete: owner, front desk, and the mechanic who uploaded the photo while still assigned. */
export function canChangePhoto(actor: Actor, order: FloorOrder, photo: Pick<Photo, 'uploadedById'>): boolean {
  return isFrontOffice(actor) || (isAssignedMechanic(actor, order) && !!photo.uploadedById && photo.uploadedById === actor.userId);
}

/** Stages this user sees and may choose: DOCUMENTS (vehicle papers) only for owner and front desk. */
export function photoStagesFor(actor: Actor): readonly PhotoStage[] {
  return isFrontOffice(actor) ? PHOTO_STAGES : PHOTO_STAGES.filter((s) => s !== 'DOCUMENTS');
}

/** Delete (with a reason): who may change the photo; once the order is delivered or cancelled only the owner. */
export function canDeletePhoto(actor: Actor, order: FloorOrder, photo: Pick<Photo, 'uploadedById'>): boolean {
  if (!canChangePhoto(actor, order, photo)) return false;
  return order.status === 'DELIVERED' || order.status === 'CANCELLED' ? actor.role === 'OWNER' : true;
}

export const DELETE_REASON_MAX = 500;

/** The size a picture is scaled to so its long side is at most `max`; never enlarged. */
export function fitWithin(width: number, height: number, max = PHOTO_MAX_EDGE): { width: number; height: number; scaled: boolean } {
  const long = Math.max(width, height);
  if (long <= max || long <= 0) return { width, height, scaled: false };
  const ratio = max / long;
  return { width: Math.max(1, Math.round(width * ratio)), height: Math.max(1, Math.round(height * ratio)), scaled: true };
}

/** The upload's file name: the original with a `.jpg` extension once it was re-encoded. */
export function jpegName(name: string): string {
  const base = name.replace(/\.[^./\\]+$/, '').replace(/[^\w.-]+/g, '_') || 'photo';
  return `${base}.jpg`;
}

// ---------------------------------------------------------------- checklists

/** Answering and starting: owner, front desk and the assigned mechanic, while the order is not delivered or cancelled. */
export function canAnswerChecklist(actor: Actor, order: FloorOrder): boolean {
  if (order.status === 'DELIVERED' || order.status === 'CANCELLED') return false;
  return isFrontOffice(actor) || isAssignedMechanic(actor, order);
}

export interface Progress {
  answered: number;
  total: number;
  percent: number;
  complete: boolean;
}

/** Required items answered of all required (N/A counts as answered). A checklist with no required item is complete. */
export function progress(c: Pick<Checklist, 'items'>): Progress {
  const required = c.items.filter((i) => i.required);
  const answered = required.filter((i) => i.result !== null).length;
  const total = required.length;
  return { answered, total, percent: total === 0 ? 100 : Math.round((answered / total) * 100), complete: answered === total };
}

/** The delivery gate the screen explains: required by the workshop and the delivery checklist is not complete. */
export function deliveryBlocked(required: boolean, checklists: readonly Checklist[]): boolean {
  if (!required) return false;
  const delivery = checklists.find((c) => c.kind === 'DELIVERY');
  return !delivery || !progress(delivery).complete;
}

export const RESULT_BADGE: Record<ChecklistResult, string> = {
  OK: 'btn-success',
  ATTENTION: 'btn-warning',
  N_A: 'btn-secondary',
};

export const RESULT_OUTLINE: Record<ChecklistResult, string> = {
  OK: 'btn-outline-success',
  ATTENTION: 'btn-outline-warning',
  N_A: 'btn-outline-secondary',
};

/**
 * The checklist as it stands with one answered item taken from a server answer. Several items can be saved at once and
 * their answers may arrive in any order, so each answer only updates its own item; the counts follow from the items.
 */
export function mergeAnswer(current: Checklist | undefined, saved: Checklist, itemId: string): Checklist {
  const answered = saved.items.find((i) => i.id === itemId);
  if (!current || !answered) return saved;
  const items = current.items.map((i) => (i.id === itemId ? answered : i));
  const p = progress({ items });
  return { ...current, items, requiredTotal: p.total, requiredAnswered: p.answered, complete: p.complete };
}

/** Replaces a checklist of the same kind, or adds it, keeping CHECK_IN first. */
export function withChecklist(list: readonly Checklist[], next: Checklist): Checklist[] {
  const rest = list.filter((c) => c.kind !== next.kind);
  return [...rest, next].sort((a, b) => kindOrder(a.kind) - kindOrder(b.kind));
}

const kindOrder = (k: ChecklistKind) => (k === 'CHECK_IN' ? 0 : 1);
