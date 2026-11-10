package com.asp.platform.billing.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.asp.platform.billing.domain.ActiveInvoiceRaceException;
import com.asp.platform.billing.domain.AlreadyInvoicedException;
import com.asp.platform.billing.domain.DocumentDraft;
import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.InvoicingProvider;
import com.asp.platform.billing.domain.InvoiceStatus;
import com.asp.platform.billing.domain.IssuedDocument;
import com.asp.platform.billing.domain.ProviderNotConfiguredException;
import com.asp.platform.billing.domain.ProviderRejectedException;
import com.asp.platform.billing.domain.ProviderUnavailableException;
import com.asp.platform.billing.domain.RequestIdReusedException;
import com.asp.platform.shared.CurrentUser;

/// Issues documents without ever losing the request. No transaction spans a whole call:
/// 1. [InvoiceService#open] stores the snapshot as PENDING and commits (the unique index makes it one per work order);
/// 2. the provider is called with the document id as its external reference, with no transaction (and no connection)
///    held open while it answers;
/// 3. the result is stored: ISSUED, FAILED (refused) or still PENDING (no answer, outcome unknown).
/// A retry repeats steps 2 and 3 on the stored document; the provider's idempotency on the external reference makes a
/// document whose answer was lost come back with its number instead of a second one.
@Service
public class InvoiceIssuing {

    private static final int MAX_MESSAGE = 300;

    private final InvoiceService service;
    private final InvoicingProvider provider;

    InvoiceIssuing(InvoiceService service, InvoicingProvider provider) {
        this.service = service;
        this.provider = provider;
    }

    /// Invoices a delivered work order. The same request id again returns the document the first call made (trying to
    /// complete it when it is not ISSUED yet); another request id while an invoice exists answers 409 already-invoiced.
    public IssueOutcome issue(CurrentUser user, UUID workOrderId, IssueRequest request) {
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<Invoice> existing = service.activeInvoice(workOrderId);
            if (existing.isPresent()) {
                return replay(user, existing.get(), request.requestId());
            }
            if (service.byRequestId(request.requestId()).isPresent()) {
                // Its document is closed (discarded or voided), or belongs to another order: a request id is used once.
                throw new RequestIdReusedException();
            }
            try {
                Invoice opened = service.open(user, workOrderId, request);
                return submit(user.tenantId(), opened.id(), true, false);
            } catch (ActiveInvoiceRaceException e) {
                // Another request stored an invoice at the same moment: read it on the next round.
            }
        }
        throw new ActiveInvoiceRaceException();
    }

    /// A full credit note of an issued invoice, with the same replay rule.
    public IssueOutcome creditNote(CurrentUser user, UUID invoiceId, UUID requestId, String reason) {
        InvoiceService.Opened opened = service.openCreditNote(user, invoiceId, requestId, reason);
        if (opened.replay()) {
            return replay(user, opened.note(), requestId);
        }
        return submit(user.tenantId(), opened.note().id(), true, false);
    }

    /// Staff retry of a PENDING or FAILED document.
    ///
    /// @throws com.asp.platform.billing.domain.InvoiceNotRetryableException for any other status
    public IssueOutcome retry(UUID tenantId, UUID id) {
        return submit(tenantId, id, false, true);
    }

    private IssueOutcome replay(CurrentUser user, Invoice existing, UUID requestId) {
        if (!existing.requestId().equals(requestId)) {
            throw new AlreadyInvoicedException(existing.id(), existing.status());
        }
        if (existing.status() == InvoiceStatus.ISSUED) {
            return new IssueOutcome(service.get(existing.id()), false);
        }
        return submit(user.tenantId(), existing.id(), false, false);
    }

    /// Asks the provider for a stored document and stores the answer.
    IssueOutcome submit(UUID tenantId, UUID id, boolean created, boolean strict) {
        InvoiceService.Prepared prepared = service.prepare(id, strict);
        DocumentDraft draft = prepared.draft();
        if (draft != null) {
            if (!provider.configured() || !provider.id().equals(prepared.invoice().provider())) {
                throw new ProviderNotConfiguredException();
            }
            if (!service.beginAttempt(id)) {
                if (strict) {
                    throw new ActiveInvoiceRaceException();
                }
                // Another call is with the provider: this one waits for its result instead of sending a second.
                return new IssueOutcome(service.get(id), created);
            }
            try {
                IssuedDocument issued = provider.issue(draft);
                service.recordIssued(tenantId, id, issued);
            } catch (ProviderRejectedException e) {
                service.recordRejected(id, failureCode(e.code()),
                        cut(e.getMessage()));
            } catch (ProviderUnavailableException e) {
                service.recordUnavailable(id, cut(e.getMessage()));
            }
        }
        return new IssueOutcome(service.get(id), created);
    }


    /// A provider code for the failed document: a default when it gave none, at most 60 characters (the column).
    static String failureCode(@org.jspecify.annotations.Nullable String code) {
        String clean = code == null ? "" : code.strip();
        if (clean.isEmpty()) {
            return "provider-error";
        }
        return clean.length() <= 60 ? clean : clean.substring(0, 60);
    }

    /// The provider's message, never null, at most [#MAX_MESSAGE] characters (the column).
    static String cut(@org.jspecify.annotations.Nullable String message) {
        String text = java.util.Objects.toString(message, "");
        return text.length() <= MAX_MESSAGE ? text : text.substring(0, MAX_MESSAGE);
    }
}
