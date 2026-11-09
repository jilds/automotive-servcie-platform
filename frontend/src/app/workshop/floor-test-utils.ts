import { HttpTestingController } from '@angular/common/http/testing';

/** The empty answers of the three floor panels of a work order, for specs that render the whole work order screen. */
export function flushFloor(http: HttpTestingController, id = 'w1'): void {
  http.expectOne(`/api/v1/work-orders/${id}/time`).flush({ now: '2026-11-09T08:00:00Z', totalSeconds: 0, unassignedSeconds: 0, byLine: [], running: null, entries: [] });
  http.expectOne(`/api/v1/work-orders/${id}/photos`).flush([]);
  http.expectOne(`/api/v1/work-orders/${id}/checklists`).flush({ deliveryChecklistRequired: false, checklists: [] });
}
