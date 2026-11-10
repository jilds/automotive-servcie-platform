package com.asp.platform.billing.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Row-level security limits every query to the current workshop.
public interface InvoiceRepository {

    /// Inserts a new document or updates the stored one (status, provider result, closing data).
    ///
    /// @throws ActiveInvoiceRaceException when another active invoice of the work order (or credit note of the
    ///                                    invoice) was stored first
    Invoice save(Invoice invoice);

    Optional<Invoice> findById(UUID id);

    /// Reads the document and locks its row until the transaction ends (`PESSIMISTIC_WRITE`; waits 3 s, then a
    /// pessimistic locking error, 409 `try-again`), so payments, credit notes, voids and provider results of one
    /// document run one after the other.
    Optional<Invoice> lockById(UUID id);

    /// The document a request id belongs to, whatever its status (a request id is used once).
    Optional<Invoice> findByRequestId(UUID requestId);

    /// The PENDING, ISSUED or FAILED invoice of the work order, if any.
    Optional<Invoice> findActiveInvoice(UUID workOrderId);

    /// The PENDING, ISSUED or FAILED credit note of the invoice, if any.
    Optional<Invoice> findActiveCreditNote(UUID invoiceId);

    /// Invoices and credit notes of the work order, newest first, with their lines.
    List<Invoice> findByWorkOrder(UUID workOrderId);

    /// The ISSUED credit notes of these invoices.
    List<Invoice> findIssuedCreditNotes(Collection<UUID> invoiceIds);

    /// PENDING documents whose last attempt was at or before `lastAttemptBefore`, with fewer than `maxAttempts`
    /// attempts, oldest attempt first.
    List<UUID> findRetryable(Instant lastAttemptBefore, int maxAttempts, int limit);
}
