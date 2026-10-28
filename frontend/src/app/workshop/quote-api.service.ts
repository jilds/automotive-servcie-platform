import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { DecisionRequest, PublicApproveRequest, PublicQuote, PublicRejectRequest, Quote, SentQuote } from './quote.models';

const API = '/api/v1';

/** The public quote API of one workshop: no token (`sendsToken` skips `/api/v1/public/`), found by its slug. */
function publicBase(slug: string, token: string): string {
  return `${API}/public/${encodeURIComponent(slug)}/quotes/${encodeURIComponent(token)}`;
}

function staffBase(workOrderId: string): string {
  return `${API}/work-orders/${encodeURIComponent(workOrderId)}/quotes`;
}

/** Quotes: sent by the front desk from a work order, decided by the customer on a private link. */
@Injectable({ providedIn: 'root' })
export class QuoteApi {
  private readonly http = inject(HttpClient);

  // Workshop (owner, front desk; mechanics 403)
  /** Freezes the lines into a new quote (409 `work-order-not-quotable`); an open quote of the order is superseded. */
  send(workOrderId: string) {
    return this.http.post<SentQuote>(staffBase(workOrderId), null);
  }
  /** Newest first, with lines. */
  list(workOrderId: string) {
    return this.http.get<Quote[]>(staffBase(workOrderId));
  }
  /** A decision taken by phone or at the counter; the note is required (`decision-note-required`). */
  decide(workOrderId: string, quoteId: string, body: DecisionRequest) {
    return this.http.post<Quote>(`${staffBase(workOrderId)}/${encodeURIComponent(quoteId)}/decision`, body);
  }

  // Public (no token)
  publicQuote(slug: string, token: string) {
    return this.http.get<PublicQuote>(publicBase(slug, token));
  }
  approve(slug: string, token: string, body: PublicApproveRequest) {
    return this.http.post<PublicQuote>(`${publicBase(slug, token)}/approve`, body);
  }
  reject(slug: string, token: string, body: PublicRejectRequest) {
    return this.http.post<PublicQuote>(`${publicBase(slug, token)}/reject`, body);
  }
}
