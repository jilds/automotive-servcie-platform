import { QuoteStatus, Role, WorkOrderStatus } from '../core/api/models';

/** Open statuses, in board column order. Delivered and cancelled orders leave the board. */
export const BOARD_COLUMNS: readonly WorkOrderStatus[] = ['DIAGNOSTICS', 'AWAITING_APPROVAL', 'AWAITING_PARTS', 'IN_PROGRESS', 'READY'];

/** Mirrors `WorkOrderStatus.next()` in the backend. The server stays the authority. */
const NEXT: Record<WorkOrderStatus, readonly WorkOrderStatus[]> = {
  DIAGNOSTICS: ['AWAITING_APPROVAL', 'IN_PROGRESS', 'CANCELLED'],
  AWAITING_APPROVAL: ['AWAITING_PARTS', 'IN_PROGRESS', 'CANCELLED'],
  AWAITING_PARTS: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['AWAITING_PARTS', 'READY'],
  READY: ['IN_PROGRESS', 'DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
};

/** Mirrors `MECHANIC_MOVES` in `WorkOrderService`: the only targets a mechanic may pick, on their own jobs. */
const MECHANIC_TARGETS: readonly WorkOrderStatus[] = ['IN_PROGRESS', 'AWAITING_PARTS', 'READY'];

export function nextStatuses(from: WorkOrderStatus): readonly WorkOrderStatus[] {
  return NEXT[from];
}

export interface Mover {
  role: Role;
  userId: string;
}

/** Whether this user may move the card at all. Mechanics may only move jobs assigned to them. */
export function canMoveCard(mover: Mover | null, assignedMechanicId: string | null): boolean {
  if (!mover) return false;
  return mover.role !== 'MECHANIC' || assignedMechanicId === mover.userId;
}

/**
 * Mirrors `WorkOrderService.move`: nobody but the owner may leave Awaiting approval until the latest quote is answered
 * (APPROVED or REJECTED). `quoteStatus` is the board card's latest quote status (EXPIRED already applied by the server).
 */
export function approvalPending(from: WorkOrderStatus, mover: Mover | null, quoteStatus: QuoteStatus | null | undefined): boolean {
  return from === 'AWAITING_APPROVAL' && mover !== null && mover.role !== 'OWNER' && quoteStatus !== 'APPROVED' && quoteStatus !== 'REJECTED';
}

/** Statuses this user may move a card to, in the order of {@link nextStatuses}. */
export function allowedMoves(
  from: WorkOrderStatus,
  mover: Mover | null,
  assignedMechanicId: string | null,
  quoteStatus?: QuoteStatus | null,
): WorkOrderStatus[] {
  if (!canMoveCard(mover, assignedMechanicId) || approvalPending(from, mover, quoteStatus)) return [];
  const next = nextStatuses(from);
  return mover!.role === 'MECHANIC' ? next.filter((s) => MECHANIC_TARGETS.includes(s)) : [...next];
}

/** The subset of {@link allowedMoves} that is a board column, so a drop target. */
export function allowedDropTargets(
  from: WorkOrderStatus,
  mover: Mover | null,
  assignedMechanicId: string | null,
  quoteStatus?: QuoteStatus | null,
): WorkOrderStatus[] {
  return allowedMoves(from, mover, assignedMechanicId, quoteStatus).filter((s) => BOARD_COLUMNS.includes(s));
}
