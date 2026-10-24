import { Role, WorkOrderStatus } from '../core/api/models';

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

/** Statuses this user may move a card to, in the order of {@link nextStatuses}. */
export function allowedMoves(from: WorkOrderStatus, mover: Mover | null, assignedMechanicId: string | null): WorkOrderStatus[] {
  if (!canMoveCard(mover, assignedMechanicId)) return [];
  const next = nextStatuses(from);
  return mover!.role === 'MECHANIC' ? next.filter((s) => MECHANIC_TARGETS.includes(s)) : [...next];
}

/** The subset of {@link allowedMoves} that is a board column, so a drop target. */
export function allowedDropTargets(from: WorkOrderStatus, mover: Mover | null, assignedMechanicId: string | null): WorkOrderStatus[] {
  return allowedMoves(from, mover, assignedMechanicId).filter((s) => BOARD_COLUMNS.includes(s));
}
