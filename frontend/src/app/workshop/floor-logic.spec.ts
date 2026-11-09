import { Checklist, ChecklistItem } from './floor.models';
import {
  Actor,
  canAnswerChecklist,
  canDeletePhoto,
  photoStagesFor,
  canChangePhoto,
  canCorrectTime,
  canStopTimer,
  canUploadPhoto,
  clock,
  clockOffset,
  deliveryBlocked,
  elapsedSeconds,
  entrySeconds,
  fitWithin,
  fromLocalInput,
  jpegName,
  liveTotal,
  mergeAnswer,
  photosLeft,
  progress,
  rangeProblem,
  timerAccess,
  toLocalInput,
  withChecklist,
} from './floor-logic';

const mechanic: Actor = { role: 'MECHANIC', userId: 'm1' };
const other: Actor = { role: 'MECHANIC', userId: 'm2' };
const owner: Actor = { role: 'OWNER', userId: 'o1' };
const desk: Actor = { role: 'FRONT_DESK', userId: 'f1' };
const mine = { status: 'IN_PROGRESS' as const, assignedMechanicId: 'm1' };

const item = (over: Partial<ChecklistItem> = {}): ChecklistItem => ({
  id: 'i1', position: 1, label: 'Chaves', required: true, result: null, note: null, answeredByName: null, answeredAt: null, ...over,
});
const list = (kind: Checklist['kind'], items: ChecklistItem[]): Checklist => ({
  kind, startedAt: '2026-11-09T08:00:00Z', startedByName: 'Rita', requiredTotal: 0, requiredAnswered: 0, complete: false, items,
});

describe('timer clock', () => {
  it('formats seconds as H:MM:SS without capping hours', () => {
    expect(clock(0)).toBe('0:00:00');
    expect(clock(59)).toBe('0:00:59');
    expect(clock(3725)).toBe('1:02:05');
    expect(clock(100 * 3600 + 1)).toBe('100:00:01');
    expect(clock(-5)).toBe('0:00:00');
    expect(clock(61.9)).toBe('0:01:01');
  });

  it('runs from the server time: the offset corrects a wrong device clock', () => {
    const deviceNow = Date.parse('2026-11-09T09:00:00Z');
    const offset = clockOffset('2026-11-09T08:51:00Z', deviceNow);
    expect(offset).toBe(-9 * 60_000);
    // 21 minutes after the start on the server clock, whatever the device says.
    expect(elapsedSeconds('2026-11-09T08:30:00Z', deviceNow + offset)).toBe(21 * 60);
    expect(clockOffset('not a date', deviceNow)).toBe(0);
  });

  it('never goes below zero before the start', () => {
    expect(elapsedSeconds('2026-11-09T08:30:00Z', Date.parse('2026-11-09T08:00:00Z'))).toBe(0);
    expect(elapsedSeconds('garbage', 1000)).toBe(0);
  });

  it('counts a running entry up to the clock and a stopped one as stored', () => {
    const now = Date.parse('2026-11-09T08:31:30Z');
    expect(entrySeconds({ running: true, startedAt: '2026-11-09T08:30:00Z', seconds: 5 }, now)).toBe(90);
    expect(entrySeconds({ running: false, startedAt: '2026-11-09T08:30:00Z', seconds: 600 }, now)).toBe(600);
  });

  it('grows a total with the clock only while something runs', () => {
    const serverNow = Date.parse('2026-11-09T08:51:00Z');
    expect(liveTotal(5400, true, serverNow, serverNow + 30_000)).toBe(5430);
    expect(liveTotal(5400, false, serverNow, serverNow + 30_000)).toBe(5400);
    expect(liveTotal(5400, true, serverNow, serverNow - 5000)).toBe(5400);
  });
});

describe('who runs the timer', () => {
  it('lets the assigned mechanic start and stop their own', () => {
    expect(timerAccess(mechanic, mine)).toEqual({ allowed: true, block: null });
    expect(canStopTimer(mechanic, mine)).toBe(true);
  });

  it('blocks another mechanic, and the front desk', () => {
    expect(timerAccess(other, mine)).toEqual({ allowed: false, block: 'not-assigned' });
    expect(canStopTimer(other, mine)).toBe(false);
    expect(timerAccess(desk, mine)).toEqual({ allowed: false, block: 'front-desk' });
    expect(canStopTimer(desk, mine)).toBe(false);
  });

  it('lets the owner run it for the assigned mechanic, but needs one', () => {
    expect(timerAccess(owner, mine).allowed).toBe(true);
    expect(canStopTimer(owner, mine)).toBe(true);
    expect(timerAccess(owner, { ...mine, assignedMechanicId: null })).toEqual({ allowed: false, block: 'no-mechanic' });
    expect(canStopTimer(owner, { ...mine, assignedMechanicId: null })).toBe(false);
  });

  it.each(['READY', 'DELIVERED', 'CANCELLED'] as const)('closes starting once the order is %s', (status) => {
    expect(timerAccess(mechanic, { ...mine, status })).toEqual({ allowed: false, block: 'closed' });
    expect(timerAccess(owner, { ...mine, status }).block).toBe('closed');
  });

  it.each(['DIAGNOSTICS', 'AWAITING_APPROVAL', 'AWAITING_PARTS', 'IN_PROGRESS'] as const)('allows starting while %s', (status) => {
    expect(timerAccess(mechanic, { ...mine, status }).allowed).toBe(true);
  });

  it('gives nobody access without a role', () => {
    expect(timerAccess({ role: null, userId: null }, mine).allowed).toBe(false);
  });

  it('lets only the front office correct time', () => {
    expect(canCorrectTime(owner)).toBe(true);
    expect(canCorrectTime(desk)).toBe(true);
    expect(canCorrectTime(mechanic)).toBe(false);
  });
});

describe('correction times', () => {
  it('round-trips an instant through the local input, minute precision', () => {
    const local = toLocalInput('2026-11-09T08:30:15Z');
    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(fromLocalInput(local)).toBe(new Date(local).toISOString());
    expect(toLocalInput(null)).toBe('');
    expect(toLocalInput('nope')).toBe('');
    expect(fromLocalInput('')).toBeNull();
  });

  it('keeps the original instant (with seconds) when the minute was not changed', () => {
    const original = '2026-11-09T08:30:15Z';
    expect(fromLocalInput(toLocalInput(original), original)).toBe(original);
    expect(fromLocalInput(toLocalInput('2026-11-09T09:30:00Z'), original)).not.toBe(original);
  });

  const now = Date.parse('2026-11-09T12:00:00Z');
  it('mirrors invalid-time-range: no future, stop not before start', () => {
    expect(rangeProblem('2026-11-09T08:00:00Z', '2026-11-09T09:00:00Z', true, now)).toBeNull();
    expect(rangeProblem('2026-11-09T13:00:00Z', null, false, now)).toEqual({ field: 'startedAt', problem: 'future' });
    expect(rangeProblem('2026-11-09T08:00:00Z', '2026-11-09T13:00:00Z', true, now)).toEqual({ field: 'stoppedAt', problem: 'future' });
    expect(rangeProblem('2026-11-09T09:00:00Z', '2026-11-09T08:00:00Z', true, now)).toEqual({ field: 'stoppedAt', problem: 'order' });
    expect(rangeProblem('2026-11-09T09:00:00Z', '2026-11-09T09:00:00Z', true, now)).toBeNull();
  });

  it('needs a start, and a stop unless the entry is still running', () => {
    expect(rangeProblem(null, null, false, now)).toEqual({ field: 'startedAt', problem: 'start-required' });
    expect(rangeProblem('2026-11-09T09:00:00Z', null, true, now)).toEqual({ field: 'stoppedAt', problem: 'stop-required' });
    expect(rangeProblem('2026-11-09T09:00:00Z', null, false, now)).toBeNull();
  });
});

describe('photos', () => {
  it('scales the long side down to 1600 and never enlarges', () => {
    expect(fitWithin(4000, 3000)).toEqual({ width: 1600, height: 1200, scaled: true });
    expect(fitWithin(3000, 4000)).toEqual({ width: 1200, height: 1600, scaled: true });
    expect(fitWithin(1600, 900)).toEqual({ width: 1600, height: 900, scaled: false });
    expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600, scaled: false });
    expect(fitWithin(100, 5000)).toEqual({ width: 32, height: 1600, scaled: true });
    expect(fitWithin(1, 5000).width).toBe(1);
    expect(fitWithin(4000, 3000, 1000)).toEqual({ width: 1000, height: 750, scaled: true });
  });

  it('counts the photos left of 30', () => {
    expect(photosLeft(0)).toBe(30);
    expect(photosLeft(29)).toBe(1);
    expect(photosLeft(30)).toBe(0);
    expect(photosLeft(40)).toBe(0);
  });

  it('names a re-encoded file as a safe .jpg', () => {
    expect(jpegName('IMG 0001.HEIC')).toBe('IMG_0001.jpg');
    expect(jpegName('')).toBe('photo.jpg');
    expect(jpegName('a/b\\c.png')).toBe('a_b_c.jpg');
  });

  it('lets the front office and the assigned mechanic upload', () => {
    expect(canUploadPhoto(owner, mine)).toBe(true);
    expect(canUploadPhoto(desk, mine)).toBe(true);
    expect(canUploadPhoto(mechanic, mine)).toBe(true);
    expect(canUploadPhoto(other, mine)).toBe(false);
    expect(canUploadPhoto({ role: null, userId: null }, mine)).toBe(false);
  });

  it('lets a mechanic change only their own photo while assigned', () => {
    expect(canChangePhoto(mechanic, mine, { uploadedById: 'm1' })).toBe(true);
    expect(canChangePhoto(mechanic, mine, { uploadedById: 'f1' })).toBe(false);
    expect(canChangePhoto(mechanic, { ...mine, assignedMechanicId: 'm2' }, { uploadedById: 'm1' })).toBe(false);
    expect(canChangePhoto(other, mine, { uploadedById: 'm2' })).toBe(false);
    expect(canChangePhoto(desk, mine, { uploadedById: 'm1' })).toBe(true);
    expect(canChangePhoto(owner, mine, { uploadedById: null })).toBe(true);
  });
});

describe('photo stages and delete', () => {
  it('shows Documents to owner and front desk only', () => {
    expect(photoStagesFor(owner)).toContain('DOCUMENTS');
    expect(photoStagesFor(desk)).toContain('DOCUMENTS');
    expect(photoStagesFor(mechanic)).toEqual(['CHECK_IN', 'WORK', 'DELIVERY']);
    expect(photoStagesFor({ role: null, userId: null })).not.toContain('DOCUMENTS');
  });

  it('lets the same people delete as change while the order is open', () => {
    expect(canDeletePhoto(mechanic, mine, { uploadedById: 'm1' })).toBe(true);
    expect(canDeletePhoto(mechanic, mine, { uploadedById: 'f1' })).toBe(false);
    expect(canDeletePhoto(desk, mine, { uploadedById: 'm1' })).toBe(true);
  });

  it.each(['DELIVERED', 'CANCELLED'] as const)('leaves deleting to the owner once %s', (status) => {
    const closed = { ...mine, status };
    expect(canDeletePhoto(owner, closed, { uploadedById: 'm1' })).toBe(true);
    expect(canDeletePhoto(desk, closed, { uploadedById: 'm1' })).toBe(false);
    expect(canDeletePhoto(mechanic, closed, { uploadedById: 'm1' })).toBe(false);
  });
});

describe('mergeAnswer', () => {
  it('takes only the answered item from a late answer and recounts', () => {
    const a = item({ id: 'a' });
    const b = item({ id: 'b' });
    const current = list('CHECK_IN', [item({ id: 'a', result: 'OK' }), b]);
    // The answer for b was made before a was answered, so it shows a open.
    const saved = list('CHECK_IN', [a, item({ id: 'b', result: 'N_A' })]);
    const merged = mergeAnswer(current, saved, 'b');
    expect(merged.items.map((i) => i.result)).toEqual(['OK', 'N_A']);
    expect(merged).toMatchObject({ requiredTotal: 2, requiredAnswered: 2, complete: true });
  });

  it('falls back to the answer when nothing is held or the item is missing', () => {
    const saved = list('CHECK_IN', [item({ id: 'a', result: 'OK' })]);
    expect(mergeAnswer(undefined, saved, 'a')).toBe(saved);
    expect(mergeAnswer(list('CHECK_IN', [item({ id: 'z' })]), saved, 'zz')).toBe(saved);
  });
});

describe('checklists', () => {
  it('counts required items answered; N/A counts; optional items do not', () => {
    const c = list('DELIVERY', [item({ id: 'a', result: 'OK' }), item({ id: 'b', result: 'N_A' }), item({ id: 'c' }), item({ id: 'd', required: false })]);
    expect(progress(c)).toEqual({ answered: 2, total: 3, percent: 67, complete: false });
    expect(progress(list('DELIVERY', [item({ result: 'ATTENTION' })]))).toEqual({ answered: 1, total: 1, percent: 100, complete: true });
  });

  it('treats a checklist without required items as complete', () => {
    expect(progress(list('DELIVERY', [item({ required: false })]))).toEqual({ answered: 0, total: 0, percent: 100, complete: true });
  });

  it('answers only for the front office and the assigned mechanic, and not once delivered or cancelled', () => {
    expect(canAnswerChecklist(mechanic, mine)).toBe(true);
    expect(canAnswerChecklist(other, mine)).toBe(false);
    expect(canAnswerChecklist(desk, mine)).toBe(true);
    expect(canAnswerChecklist(owner, { ...mine, status: 'DELIVERED' })).toBe(false);
    expect(canAnswerChecklist(owner, { ...mine, status: 'CANCELLED' })).toBe(false);
    expect(canAnswerChecklist(owner, { ...mine, status: 'READY' })).toBe(true);
  });

  it('explains the delivery gate: required and delivery checklist missing or incomplete', () => {
    const open = list('DELIVERY', [item()]);
    const done = list('DELIVERY', [item({ result: 'OK' })]);
    const checkIn = list('CHECK_IN', [item()]);
    expect(deliveryBlocked(false, [])).toBe(false);
    expect(deliveryBlocked(true, [])).toBe(true);
    expect(deliveryBlocked(true, [checkIn])).toBe(true);
    expect(deliveryBlocked(true, [open])).toBe(true);
    expect(deliveryBlocked(true, [done])).toBe(false);
  });

  it('replaces a checklist of the same kind and keeps check-in first', () => {
    const a = list('DELIVERY', [item()]);
    const b = list('CHECK_IN', [item()]);
    const a2 = list('DELIVERY', [item({ result: 'OK' })]);
    expect(withChecklist([a], b).map((c) => c.kind)).toEqual(['CHECK_IN', 'DELIVERY']);
    expect(withChecklist([b, a], a2)).toHaveLength(2);
    expect(withChecklist([b, a], a2)[1]).toBe(a2);
  });
});
