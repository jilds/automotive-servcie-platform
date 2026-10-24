import { WorkOrderStatus } from '../core/api/models';
import { allowedDropTargets, allowedMoves, canMoveCard, Mover, nextStatuses } from './board-moves';

const owner: Mover = { role: 'OWNER', userId: 'o1' };
const frontDesk: Mover = { role: 'FRONT_DESK', userId: 'f1' };
const mechanic: Mover = { role: 'MECHANIC', userId: 'm1' };

describe('board moves', () => {
  it('mirrors the backend transitions', () => {
    const expected: Record<WorkOrderStatus, WorkOrderStatus[]> = {
      DIAGNOSTICS: ['AWAITING_APPROVAL', 'IN_PROGRESS', 'CANCELLED'],
      AWAITING_APPROVAL: ['AWAITING_PARTS', 'IN_PROGRESS', 'CANCELLED'],
      AWAITING_PARTS: ['IN_PROGRESS', 'CANCELLED'],
      IN_PROGRESS: ['AWAITING_PARTS', 'READY'],
      READY: ['IN_PROGRESS', 'DELIVERED'],
      DELIVERED: [],
      CANCELLED: [],
    };
    for (const [from, next] of Object.entries(expected)) {
      expect(nextStatuses(from as WorkOrderStatus)).toEqual(next);
    }
  });

  it('lets owner and front desk use every transition on any card', () => {
    expect(allowedMoves('DIAGNOSTICS', owner, 'm1')).toEqual(['AWAITING_APPROVAL', 'IN_PROGRESS', 'CANCELLED']);
    expect(allowedMoves('READY', frontDesk, null)).toEqual(['IN_PROGRESS', 'DELIVERED']);
  });

  it('limits a mechanic to In progress, Awaiting parts and Ready on their own card', () => {
    expect(allowedMoves('DIAGNOSTICS', mechanic, 'm1')).toEqual(['IN_PROGRESS']);
    expect(allowedMoves('AWAITING_APPROVAL', mechanic, 'm1')).toEqual(['AWAITING_PARTS', 'IN_PROGRESS']);
    expect(allowedMoves('IN_PROGRESS', mechanic, 'm1')).toEqual(['AWAITING_PARTS', 'READY']);
    expect(allowedMoves('READY', mechanic, 'm1')).toEqual(['IN_PROGRESS']);
  });

  it('gives a mechanic no moves on someone else’s card or an unassigned one', () => {
    expect(canMoveCard(mechanic, 'm2')).toBe(false);
    expect(allowedMoves('IN_PROGRESS', mechanic, 'm2')).toEqual([]);
    expect(allowedMoves('IN_PROGRESS', mechanic, null)).toEqual([]);
  });

  it('gives no moves without a signed-in user', () => {
    expect(allowedMoves('DIAGNOSTICS', null, null)).toEqual([]);
  });

  it('only offers board columns as drop targets', () => {
    expect(allowedDropTargets('DIAGNOSTICS', owner, null)).toEqual(['AWAITING_APPROVAL', 'IN_PROGRESS']);
    expect(allowedDropTargets('READY', owner, null)).toEqual(['IN_PROGRESS']);
  });
});
