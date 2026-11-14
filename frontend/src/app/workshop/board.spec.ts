import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { BoardCard, User, WorkOrderStatus } from '../core/api/models';
import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { Board } from './board';

function card(id: string, number: number, assignedMechanicId: string | null = null): BoardCard {
  return {
    id,
    number,
    plate: '11AA11',
    plateDisplay: '11-AA-11',
    plateEra: 'PT_2005',
    registrationCountry: 'PT',
    registrationDate: null,
    vehicle: 'Renault Clio',
    assignedMechanicId,
    statusChangedAt: '2026-10-21T08:00:00Z',
  };
}

function user(role: User['role'], id = 'u1'): User {
  return { id, tenantId: 't1', name: 'Ana', email: 'ana@example.test', role } as User;
}

describe('Board', () => {
  let http: HttpTestingController;

  function open(who: User, lang: 'en' | 'pt-PT' = 'en') {
    try {
      localStorage.clear();
    } catch {
      // ignore
    }
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { user: signal(who) } },
      ],
    });
    TestBed.inject(I18nService).use(lang);
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Board);
    fixture.detectChanges();
    http.expectOne('/api/v1/work-orders/board').flush({
      columns: {
        DIAGNOSTICS: [{ ...card('w1', 1, 'm1'), customerName: 'Rui Costa' }, card('w2', 2)],
        AWAITING_APPROVAL: [],
        AWAITING_PARTS: [],
        IN_PROGRESS: [card('w3', 3, 'm1')],
        READY: [],
        DELIVERED: [card('w9', 9)],
      },
    });
    fixture.detectChanges();
    return fixture;
  }

  const text = (el: HTMLElement) => (el.textContent ?? '').replace(/\s+/g, ' ');
  const count = (el: HTMLElement, status: WorkOrderStatus) =>
    el.querySelector(`[data-status="${status}"] .badge`)?.textContent?.trim();
  const columnOf = (el: HTMLElement, status: WorkOrderStatus) => el.querySelector(`[data-status="${status}"]`) as HTMLElement;

  /** Fakes the CDK objects the enter predicate and drop handler read. */
  const drag = (data: BoardCard, from: WorkOrderStatus) => ({ data, dropContainer: { data: from } }) as unknown as CdkDrag<BoardCard>;
  const list = (data: WorkOrderStatus) => ({ data }) as unknown as CdkDropList<WorkOrderStatus>;
  const drop = (data: BoardCard, from: WorkOrderStatus, to: WorkOrderStatus) =>
    ({ item: { data }, previousContainer: list(from), container: from === to ? list(from) : list(to), currentIndex: 0 }) as unknown as CdkDragDrop<WorkOrderStatus, WorkOrderStatus, BoardCard>;

  afterEach(() => http.verify());

  describe('enter predicate', () => {
    it('lets an owner drop on every allowed column and back on its own', () => {
      const board = open(user('OWNER')).componentInstance;
      const d = drag(card('w2', 2), 'DIAGNOSTICS');
      expect(board.canEnter(d, list('AWAITING_APPROVAL'))).toBe(true);
      expect(board.canEnter(d, list('IN_PROGRESS'))).toBe(true);
      expect(board.canEnter(d, list('DIAGNOSTICS'))).toBe(true);
      expect(board.canEnter(d, list('AWAITING_PARTS'))).toBe(false);
      expect(board.canEnter(d, list('READY'))).toBe(false);
    });

    it('applies the same rules to front desk', () => {
      const board = open(user('FRONT_DESK')).componentInstance;
      expect(board.canEnter(drag(card('w3', 3), 'IN_PROGRESS'), list('READY'))).toBe(true);
      expect(board.canEnter(drag(card('w3', 3), 'IN_PROGRESS'), list('DIAGNOSTICS'))).toBe(false);
    });

    it('limits a mechanic to their own cards and to the mechanic statuses', () => {
      const board = open(user('MECHANIC', 'm1')).componentInstance;
      expect(board.canEnter(drag(card('w1', 1, 'm1'), 'DIAGNOSTICS'), list('IN_PROGRESS'))).toBe(true);
      expect(board.canEnter(drag(card('w1', 1, 'm1'), 'DIAGNOSTICS'), list('AWAITING_APPROVAL'))).toBe(false);
      expect(board.canEnter(drag(card('w2', 2, 'm2'), 'DIAGNOSTICS'), list('IN_PROGRESS'))).toBe(false);
    });
  });

  it('moves the card at once and keeps the counts in sync', () => {
    const fixture = open(user('OWNER'));
    fixture.componentInstance.dropped(drop(card('w2', 2), 'DIAGNOSTICS', 'IN_PROGRESS'));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(count(el, 'DIAGNOSTICS')).toBe('1');
    expect(count(el, 'IN_PROGRESS')).toBe('2');
    expect(columnOf(el, 'IN_PROGRESS').querySelector('.wo-card')?.getAttribute('aria-busy')).toBe('true');

    const req = http.expectOne('/api/v1/work-orders/w2/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'IN_PROGRESS' });
    req.flush({ id: 'w2', status: 'IN_PROGRESS', statusChangedAt: '2026-10-21T09:00:00Z' });
    fixture.detectChanges();
    expect(columnOf(el, 'IN_PROGRESS').querySelector('.wo-card')?.getAttribute('aria-busy')).toBe('false');
    expect(text(el.querySelector('[aria-live]')!)).toContain('Work order #2 moved to In progress.');
  });

  it('rolls the card back and shows the translated error when the server refuses', () => {
    const fixture = open(user('OWNER'), 'pt-PT');
    fixture.componentInstance.dropped(drop(card('w2', 2), 'DIAGNOSTICS', 'IN_PROGRESS'));
    http.expectOne('/api/v1/work-orders/w2/status').flush({ code: 'illegal-status-change' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(count(el, 'DIAGNOSTICS')).toBe('2');
    expect(count(el, 'IN_PROGRESS')).toBe('1');
    const cards = [...columnOf(el, 'DIAGNOSTICS').querySelectorAll('.wo-card')].map((c) => text(c as HTMLElement));
    expect(cards[1]).toContain('Renault Clio');
    expect(text(el.querySelector('app-alert')!)).toContain('não foi movida. A ordem de reparação não pode passar para esse estado.');

    // The board is out of date, so it reloads; the message stays.
    http.expectOne('/api/v1/work-orders/board').flush({ columns: { DIAGNOSTICS: [card('w1', 1, 'm1')] } });
    fixture.detectChanges();
    expect(count(el, 'DIAGNOSTICS')).toBe('1');
    expect(el.querySelector('app-alert')).not.toBeNull();
  });

  it('rolls back on 403 with the permission message', () => {
    const fixture = open(user('MECHANIC', 'm1'));
    fixture.componentInstance.dropped(drop(card('w1', 1, 'm1'), 'DIAGNOSTICS', 'IN_PROGRESS'));
    http.expectOne('/api/v1/work-orders/w1/status').flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(count(el, 'DIAGNOSTICS')).toBe('2');
    expect(text(el.querySelector('app-alert')!)).toContain('You do not have permission to do this.');
  });

  it('sends nothing for a drop in the same column, a refused target or a card already moving', () => {
    const fixture = open(user('OWNER'));
    const board = fixture.componentInstance;
    board.dropped(drop(card('w2', 2), 'DIAGNOSTICS', 'DIAGNOSTICS'));
    board.dropped(drop(card('w2', 2), 'DIAGNOSTICS', 'READY'));
    http.expectNone('/api/v1/work-orders/w2/status');

    board.move(card('w3', 3), 'IN_PROGRESS', 'READY');
    board.move(card('w3', 3), 'READY', 'IN_PROGRESS');
    http.expectOne('/api/v1/work-orders/w3/status').flush({ id: 'w3', status: 'READY', statusChangedAt: '2026-10-21T09:00:00Z' });
  });

  it('offers only the allowed statuses in the Move to select, and moves from it', () => {
    const fixture = open(user('MECHANIC', 'm1'));
    const el = fixture.nativeElement as HTMLElement;
    const select = columnOf(el, 'IN_PROGRESS').querySelector('select') as HTMLSelectElement;
    expect([...select.options].map((o) => o.value)).toEqual(['', 'AWAITING_PARTS', 'READY']);
    expect(select.getAttribute('aria-label')).toBe('Move work order #3');

    select.value = 'READY';
    select.dispatchEvent(new Event('change'));
    http.expectOne('/api/v1/work-orders/w3/status').flush({ id: 'w3', status: 'READY', statusChangedAt: '2026-10-21T09:00:00Z' });
    fixture.detectChanges();
    expect(count(el, 'READY')).toBe('1');
    expect(count(el, 'IN_PROGRESS')).toBe('0');
  });

  it('gives a mechanic no drag and no select on a card that is not theirs', () => {
    const fixture = open(user('MECHANIC', 'm1'));
    const el = fixture.nativeElement as HTMLElement;
    const other = columnOf(el, 'DIAGNOSTICS').querySelectorAll('.wo-card')[1] as HTMLElement;
    expect(other.classList).not.toContain('is-movable');
    expect(other.classList).toContain('cdk-drag-disabled');
    expect(other.querySelector('select')).toBeNull();
  });

  describe('Awaiting approval', () => {
    const waiting = (quoteStatus: BoardCard['quoteStatus']): BoardCard => ({ ...card('w9', 9), quoteStatus });

    it('blocks front desk drops and the select until the quote is answered, and frees them after', () => {
      const board = open(user('FRONT_DESK')).componentInstance;
      for (const q of [undefined, null, 'SENT', 'EXPIRED', 'SUPERSEDED'] as const) {
        expect(board.canEnter(drag(waiting(q), 'AWAITING_APPROVAL'), list('IN_PROGRESS'))).toBe(false);
        expect(board.canEnter(drag(waiting(q), 'AWAITING_APPROVAL'), list('AWAITING_APPROVAL'))).toBe(true);
      }
      expect(board.canEnter(drag(waiting('APPROVED'), 'AWAITING_APPROVAL'), list('IN_PROGRESS'))).toBe(true);
      expect(board.canEnter(drag(waiting('REJECTED'), 'AWAITING_APPROVAL'), list('AWAITING_PARTS'))).toBe(true);
    });

    it('does not make a locked card draggable or movable-styled, and frees it once answered', () => {
      const fixture = open(user('FRONT_DESK'));
      const el = fixture.nativeElement as HTMLElement;
      const cardEl = () => columnOf(el, 'AWAITING_APPROVAL').querySelector('.wo-card') as HTMLElement;
      const show = (q: BoardCard['quoteStatus']) => {
        fixture.componentInstance.load();
        http.expectOne('/api/v1/work-orders/board').flush({ columns: { AWAITING_APPROVAL: [waiting(q)] } });
        fixture.detectChanges();
      };
      show('SENT');
      expect(cardEl().classList).not.toContain('is-movable');
      expect(cardEl().classList).toContain('cdk-drag-disabled');
      expect(cardEl().querySelector('select')).toBeNull();
      show('APPROVED');
      expect(cardEl().classList).toContain('is-movable');
      expect(cardEl().classList).not.toContain('cdk-drag-disabled');
      expect(cardEl().querySelector('select')).not.toBeNull();
    });

    it('lets the owner move regardless of the quote', () => {
      const board = open(user('OWNER')).componentInstance;
      expect(board.canEnter(drag(waiting('SENT'), 'AWAITING_APPROVAL'), list('IN_PROGRESS'))).toBe(true);
    });

    it('sends nothing for a blocked front desk move', () => {
      const board = open(user('FRONT_DESK')).componentInstance;
      board.move(waiting('SENT'), 'AWAITING_APPROVAL', 'IN_PROGRESS');
      http.expectNone('/api/v1/work-orders/w9/status');
    });

    it('rolls back and shows the translated error when the server answers approval-pending', () => {
      const fixture = open(user('FRONT_DESK'));
      // The client believed the quote was answered; the server disagrees (it is the authority).
      fixture.componentInstance.move(waiting('APPROVED'), 'AWAITING_APPROVAL', 'IN_PROGRESS');
      http.expectOne('/api/v1/work-orders/w9/status').flush({ code: 'approval-pending' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();
      expect(text(fixture.nativeElement.querySelector('app-alert')!)).toContain('cannot leave Awaiting approval until the customer has answered the quote');
      http.expectOne('/api/v1/work-orders/board').flush({ columns: {} });
    });
  });

  it('takes a delivered or cancelled card off the board', () => {
    const fixture = open(user('FRONT_DESK'));
    fixture.componentInstance.move(card('w2', 2), 'DIAGNOSTICS', 'CANCELLED');
    http.expectOne('/api/v1/work-orders/w2/status').flush({ id: 'w2', status: 'CANCELLED', statusChangedAt: '2026-10-21T09:00:00Z' });
    fixture.detectChanges();
    expect(count(fixture.nativeElement, 'DIAGNOSTICS')).toBe('1');
  });

  it('shows the customer name under the vehicle, only when the server sent it', () => {
    const fixture = open(user('FRONT_DESK'));
    const names = fixture.nativeElement.querySelectorAll('[data-test="customer-name"]');
    expect(names.length).toBe(1);
    expect(text(names[0])).toContain('Rui Costa');
  });

  describe('delivered column', () => {
    it('hides one delivered card for the front desk, keeping the order', () => {
      const fixture = open(user('FRONT_DESK'));
      expect(count(fixture.nativeElement, 'DELIVERED')).toBe('1');
      (columnOf(fixture.nativeElement, 'DELIVERED').querySelector('[data-test="hide"]') as HTMLButtonElement).click();
      http.expectOne('/api/v1/work-orders/w9/board-hide').flush(null, { status: 204, statusText: 'No Content' });
      fixture.detectChanges();
      expect(count(fixture.nativeElement, 'DELIVERED')).toBe('0');
    });

    it('hides all delivered cards at once', () => {
      const fixture = open(user('OWNER'));
      ((fixture.nativeElement as HTMLElement).querySelector('[data-test="hide-all"]') as HTMLButtonElement).click();
      http.expectOne('/api/v1/work-orders/board-hide-delivered').flush({ hidden: 1 });
      fixture.detectChanges();
      expect(count(fixture.nativeElement, 'DELIVERED')).toBe('0');
    });

    it('gives a mechanic no hide action', () => {
      const fixture = open(user('MECHANIC', 'm1'));
      expect(columnOf(fixture.nativeElement, 'DELIVERED').querySelector('[data-test="hide"]')).toBeNull();
      expect((fixture.nativeElement as HTMLElement).querySelector('[data-test="hide-all"]')).toBeNull();
    });
  });
});
