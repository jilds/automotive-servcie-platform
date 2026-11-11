import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, from, map, Observable, switchMap, throwError } from 'rxjs';

import { costVsQuoteParams, filenameFromDisposition, periodParams, productivityParams } from './reporting-logic';
import type {
  CostVsQuoteQuery,
  CostVsQuoteReport,
  KpiReport,
  PeriodQuery,
  ProductivityQuery,
  ProductivityReport,
  ReportKind,
} from './reporting.models';

const API = '/api/v1/reports';

/** A CSV as the server sent it: the blob and the file name of its `Content-Disposition`. */
export interface CsvDownload {
  blob: Blob;
  fileName: string;
}

/** The three reports of the owner (OWNER only; everyone else gets 403). Read-only: `GET` only. */
@Injectable({ providedIn: 'root' })
export class ReportingApi {
  private readonly http = inject(HttpClient);

  costVsQuote(q: CostVsQuoteQuery) {
    return this.http.get<CostVsQuoteReport>(`${API}/cost-vs-quote`, { params: costVsQuoteParams(q) });
  }

  productivity(q: ProductivityQuery) {
    return this.http.get<ProductivityReport>(`${API}/productivity`, { params: productivityParams(q) });
  }

  kpis(q: PeriodQuery) {
    return this.http.get<KpiReport>(`${API}/kpis`, { params: periodParams(q) });
  }

  /**
   * `?format=csv` of a report with the same filters as the screen, fetched with the token (so not a plain link) and
   * returned as a blob. The CSV is not paged. An error body arrives as a blob and is turned back into a problem, so
   * `report-range-too-large` and `report-too-many-rows` reach the error texts.
   */
  csv(kind: ReportKind, q: CostVsQuoteQuery | ProductivityQuery | PeriodQuery): Observable<CsvDownload> {
    let params: HttpParams;
    if (kind === 'cost-vs-quote') params = costVsQuoteParams(q as CostVsQuoteQuery, false);
    else if (kind === 'productivity') params = productivityParams(q as ProductivityQuery);
    else params = periodParams(q);
    params = params.set('format', 'csv');
    return this.http.get(`${API}/${kind}`, { params, responseType: 'blob', observe: 'response' }).pipe(
      map((res) => ({
        blob: res.body as Blob,
        fileName: filenameFromDisposition(res.headers.get('Content-Disposition'), `${kind}.csv`),
      })),
      catchError(problemFromBlob),
    );
  }
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
