import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, from, map, Observable, switchMap, throwError } from 'rxjs';

import {
  BillingSettings,
  CreatePaymentRequest,
  Invoice,
  InvoicePage,
  InvoiceQuery,
  IssueInvoiceRequest,
  IssueResult,
  Payment,
  SandboxOutcome,
} from './billing.models';
import { outcomeOf } from './billing-logic';

const API = '/api/v1';
const enc = encodeURIComponent;

/** Invoicing and payments for the owner and the front desk (mechanics get 403 on every route). */
@Injectable({ providedIn: 'root' })
export class BillingApi {
  private readonly http = inject(HttpClient);

  settings() {
    return this.http.get<BillingSettings>(`${API}/billing/settings`);
  }

  /** 201 new and issued, 200 replay of an issued one, 202 stored as PENDING or FAILED. */
  issue(workOrderId: string, body: IssueInvoiceRequest): Observable<IssueResult> {
    return this.http
      .post<Invoice>(`${API}/work-orders/${enc(workOrderId)}/invoices`, body, { observe: 'response' })
      .pipe(map(asResult));
  }
  ofWorkOrder(workOrderId: string) {
    return this.http.get<Invoice[]>(`${API}/work-orders/${enc(workOrderId)}/invoices`);
  }

  list(q: InvoiceQuery = {}) {
    let params = new HttpParams();
    for (const s of q.status ?? []) params = params.append('status', s);
    if (q.kind) params = params.set('kind', q.kind);
    if (q.paymentStatus) params = params.set('paymentStatus', q.paymentStatus);
    if (q.from) params = params.set('from', q.from);
    if (q.to) params = params.set('to', q.to);
    const search = q.q?.trim();
    if (search && search.length >= 2) params = params.set('q', search);
    params = params.set('page', q.page ?? 0).set('size', q.size ?? 20);
    return this.http.get<InvoicePage>(`${API}/invoices`, { params });
  }
  get(id: string) {
    return this.http.get<Invoice>(`${API}/invoices/${enc(id)}`);
  }

  /** The PDF as a blob, fetched with the token; an error body (a blob) is turned back into a problem. */
  pdf(id: string): Observable<Blob> {
    return this.http.get(`${API}/invoices/${enc(id)}/pdf`, { responseType: 'blob' }).pipe(catchError(problemFromBlob));
  }

  retry(id: string) {
    return this.http.post<Invoice>(`${API}/invoices/${enc(id)}/retry`, null);
  }
  discard(id: string, reason: string, version: number) {
    return this.http.post<Invoice>(`${API}/invoices/${enc(id)}/discard`, { reason, version });
  }
  void(id: string, reason: string, version: number) {
    return this.http.post<Invoice>(`${API}/invoices/${enc(id)}/void`, { reason, version });
  }
  /** Returns the credit note; 201 / 200 / 202 like an issue. */
  creditNote(id: string, requestId: string, reason: string): Observable<IssueResult> {
    return this.http
      .post<Invoice>(`${API}/invoices/${enc(id)}/credit-notes`, { requestId, reason }, { observe: 'response' })
      .pipe(map(asResult));
  }

  createPayment(invoiceId: string, body: CreatePaymentRequest) {
    return this.http.post<Payment>(`${API}/invoices/${enc(invoiceId)}/payments`, body);
  }
  payment(id: string) {
    return this.http.get<Payment>(`${API}/payments/${enc(id)}`);
  }
  refreshPayment(id: string) {
    return this.http.post<Payment>(`${API}/payments/${enc(id)}/refresh`, null);
  }
  cancelPayment(id: string, version: number) {
    return this.http.post<Payment>(`${API}/payments/${enc(id)}/cancel`, { version });
  }
  /** Sandbox only: the route does not exist (404) when `payments.sandbox` is false. */
  simulatePayment(id: string, outcome: SandboxOutcome) {
    return this.http.post<Payment>(`${API}/payments/${enc(id)}/sandbox/simulate`, { outcome });
  }
}

function asResult(r: HttpResponse<Invoice>): IssueResult {
  return { outcome: outcomeOf(r.status), invoice: r.body as Invoice };
}

/** A failed blob request carries its problem JSON as a Blob: read it so `code` reaches the error texts. */
export function problemFromBlob(err: unknown): Observable<never> {
  if (!(err instanceof HttpErrorResponse) || !(err.error instanceof Blob)) return throwError(() => err);
  return from(err.error.text()).pipe(
    map((text) => {
      try {
        return JSON.parse(text) as unknown;
      } catch {
        return null;
      }
    }),
    switchMap((body) =>
      throwError(() => new HttpErrorResponse({ error: body, status: err.status, statusText: err.statusText, url: err.url ?? undefined })),
    ),
  );
}
