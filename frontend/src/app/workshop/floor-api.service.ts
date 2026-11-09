import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
  AnswerRequest,
  Checklist,
  ChecklistKind,
  ChecklistTemplate,
  MissedTimeRequest,
  Photo,
  PhotoStage,
  PhotoUpdateRequest,
  RunningTimer,
  StartTimeRequest,
  TimeEntryChange,
  TemplateRequest,
  TimeEntry,
  TimeEntryRequest,
  TimeSummary,
  WorkOrderChecklists,
} from './floor.models';

const API = '/api/v1';

const order = (id: string) => `${API}/work-orders/${encodeURIComponent(id)}`;

/** Time tracking, photos and checklists of a work order, and the checklist templates (owner). */
@Injectable({ providedIn: 'root' })
export class FloorApi {
  private readonly http = inject(HttpClient);

  // Time
  time(workOrderId: string) {
    return this.http.get<TimeSummary>(`${order(workOrderId)}/time`);
  }
  /** 201 for a new entry, 200 when the same order and line already run. */
  startTimer(workOrderId: string, body: StartTimeRequest) {
    return this.http.post<TimeEntry>(`${order(workOrderId)}/time/start`, body);
  }
  stopTimer(workOrderId: string) {
    return this.http.post<TimeEntry>(`${order(workOrderId)}/time/stop`, null);
  }
  /** The signed-in user's running entry on any order. */
  running() {
    return this.http.get<RunningTimer>(`${API}/time/running`);
  }
  addMissedTime(workOrderId: string, body: MissedTimeRequest) {
    return this.http.post<TimeEntry>(`${order(workOrderId)}/time-entries`, body);
  }
  correctTime(workOrderId: string, entryId: string, body: TimeEntryRequest) {
    return this.http.put<TimeEntry>(`${order(workOrderId)}/time-entries/${encodeURIComponent(entryId)}`, body);
  }

  /** What an entry was before each correction, oldest first (owner, front desk). */
  timeChanges(workOrderId: string, entryId: string) {
    return this.http.get<TimeEntryChange[]>(`${order(workOrderId)}/time-entries/${encodeURIComponent(entryId)}/changes`);
  }

  // Photos
  photos(workOrderId: string) {
    return this.http.get<Photo[]>(`${order(workOrderId)}/photos`);
  }
  uploadPhoto(workOrderId: string, file: Blob, fileName: string, stage: PhotoStage, caption: string | null) {
    const form = new FormData();
    form.append('file', file, fileName);
    form.append('stage', stage);
    if (caption?.trim()) form.append('caption', caption.trim());
    return this.http.post<Photo>(`${order(workOrderId)}/photos`, form);
  }
  updatePhoto(workOrderId: string, photoId: string, body: PhotoUpdateRequest) {
    return this.http.put<Photo>(`${order(workOrderId)}/photos/${encodeURIComponent(photoId)}`, body);
  }
  /** A soft delete with a required reason (1 to 500 characters) in the body. */
  deletePhoto(workOrderId: string, photoId: string, reason: string) {
    return this.http.delete<void>(`${order(workOrderId)}/photos/${encodeURIComponent(photoId)}`, { body: { reason } });
  }
  /**
   * The bytes of a photo. An `<img>` cannot send the bearer token, so they are fetched here and shown as an object
   * URL. With the ETag of the copy already held, an unchanged photo answers 304 (an error response for HttpClient).
   */
  photoContent(workOrderId: string, photoId: string, etag: string | null) {
    const headers = etag ? new HttpHeaders({ 'If-None-Match': etag }) : undefined;
    return this.http.get(`${order(workOrderId)}/photos/${encodeURIComponent(photoId)}/content`, {
      responseType: 'blob',
      observe: 'response',
      headers,
    });
  }

  // Checklists of an order
  checklists(workOrderId: string) {
    return this.http.get<WorkOrderChecklists>(`${order(workOrderId)}/checklists`);
  }
  startChecklist(workOrderId: string, kind: ChecklistKind) {
    return this.http.post<Checklist>(`${order(workOrderId)}/checklists/${kind}`, null);
  }
  answer(workOrderId: string, kind: ChecklistKind, itemId: string, body: AnswerRequest) {
    return this.http.put<Checklist>(`${order(workOrderId)}/checklists/${kind}/items/${encodeURIComponent(itemId)}`, body);
  }

  // Templates (owner writes; owner and front desk read)
  templates() {
    return this.http.get<ChecklistTemplate[]>(`${API}/checklist-templates`);
  }
  saveTemplate(kind: ChecklistKind, body: TemplateRequest) {
    return this.http.put<ChecklistTemplate>(`${API}/checklist-templates/${kind}`, body);
  }
}
