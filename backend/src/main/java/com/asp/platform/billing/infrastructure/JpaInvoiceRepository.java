package com.asp.platform.billing.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.asp.platform.billing.domain.ActiveInvoiceRaceException;
import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.InvoiceRepository;
import com.asp.platform.shared.persistence.Constraints;
import com.asp.platform.shared.persistence.Upserts;

@Repository
class JpaInvoiceRepository implements InvoiceRepository {

    /// The partial unique indexes of V20.
    static final String ONE_PER_WORK_ORDER = "invoice_one_active_per_work_order_idx";
    static final String ONE_CREDIT_NOTE = "invoice_one_active_credit_note_idx";
    static final String ONE_REQUEST = "invoice_tenant_request_key";

    private final SpringDataInvoiceRepository jpa;

    JpaInvoiceRepository(SpringDataInvoiceRepository jpa) {
        this.jpa = jpa;
    }

    /// Two requests that open a document for the same work order (or credit note for the same invoice) at the same
    /// moment: the unique index lets one in; the other gets 409 try-again and reads the winner.
    @Override
    public Invoice save(Invoice invoice) {
        try {
            return Upserts.upsert(jpa, invoice.id(), invoice, InvoiceEntity::new);
        } catch (DataIntegrityViolationException e) {
            if (Constraints.violated(e, ONE_PER_WORK_ORDER) || Constraints.violated(e, ONE_CREDIT_NOTE)
                    || Constraints.violated(e, ONE_REQUEST)) {
                throw new ActiveInvoiceRaceException();
            }
            throw e;
        }
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return jpa.findById(id).map(InvoiceEntity::toDomain);
    }

    @Override
    public Optional<Invoice> lockById(UUID id) {
        return jpa.lockById(id).map(InvoiceEntity::toDomain);
    }

    @Override
    public Optional<Invoice> findByRequestId(UUID requestId) {
        return jpa.findByRequestId(requestId).map(InvoiceEntity::toDomain);
    }

    @Override
    public Optional<Invoice> findActiveInvoice(UUID workOrderId) {
        return jpa.findActiveInvoice(workOrderId).map(InvoiceEntity::toDomain);
    }

    @Override
    public Optional<Invoice> findActiveCreditNote(UUID invoiceId) {
        return jpa.findActiveCreditNote(invoiceId).map(InvoiceEntity::toDomain);
    }

    @Override
    public List<Invoice> findByWorkOrder(UUID workOrderId) {
        return jpa.findByWorkOrderIdOrderByCreatedAtDescIdDesc(workOrderId).stream().map(InvoiceEntity::toDomain)
                .toList();
    }

    @Override
    public List<Invoice> findIssuedCreditNotes(Collection<UUID> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return List.of();
        }
        return jpa.findIssuedCreditNotes(invoiceIds).stream().map(InvoiceEntity::toDomain).toList();
    }

    @Override
    public List<UUID> findRetryable(Instant lastAttemptBefore, int maxAttempts, int limit) {
        return jpa.findRetryable(lastAttemptBefore, maxAttempts, limit);
    }
}
