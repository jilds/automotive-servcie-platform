package com.asp.platform.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.billing.InvoiceIssued;
import com.asp.platform.billing.domain.ActiveInvoiceRaceException;
import com.asp.platform.billing.domain.BillingWorkOrderNotFoundException;
import com.asp.platform.billing.domain.CreditNoteExistsException;
import com.asp.platform.billing.domain.DocumentDraft;
import com.asp.platform.billing.domain.DocumentKind;
import com.asp.platform.billing.domain.FiscalAddress;
import com.asp.platform.billing.domain.FiscalRules;
import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.InvoiceHasPaymentsException;
import com.asp.platform.billing.domain.InvoiceLine;
import com.asp.platform.billing.domain.InvoiceNotFoundException;
import com.asp.platform.billing.domain.InvoiceNotIssuedException;
import com.asp.platform.billing.domain.InvoiceNotVoidableException;
import com.asp.platform.billing.domain.InvoiceQueries;
import com.asp.platform.billing.domain.InvoiceRepository;
import com.asp.platform.billing.domain.InvoiceStatus;
import com.asp.platform.billing.domain.InvoiceTotals;
import com.asp.platform.billing.domain.InvoicingProvider;
import com.asp.platform.billing.domain.IssuedDocument;
import com.asp.platform.billing.domain.IssuerSnapshot;
import com.asp.platform.billing.domain.LineKind;
import com.asp.platform.billing.domain.Payment;
import com.asp.platform.billing.domain.PaymentProgress;
import com.asp.platform.billing.domain.PaymentRepository;
import com.asp.platform.billing.domain.PaymentStatus;
import com.asp.platform.billing.domain.ProviderNotConfiguredException;
import com.asp.platform.billing.domain.ProviderRejectedConflictException;
import com.asp.platform.billing.domain.ProviderRejectedException;
import com.asp.platform.billing.domain.RequestIdReusedException;
import com.asp.platform.billing.domain.Settlement;
import com.asp.platform.billing.domain.WorkOrderNotInvoiceableException;
import com.asp.platform.customer.CustomerFiscalData;
import com.asp.platform.customer.CustomerManagement;
import com.asp.platform.customer.VehicleSummary;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.identity.WorkshopFiscalProfile;
import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.BillableWorkOrder;
import com.asp.platform.workshop.WorkOrderBilling;

/// The documents of the billing module: storing a new invoice or credit note before the provider is asked, recording
/// the provider's answer, voiding, discarding, and the reads the screens use. Every change that touches an existing
/// document locks its row first ([InvoiceRepository#lockById], 3 s, then 409 `try-again`), so payments, credit notes,
/// voids and provider results of one document run one after the other. The provider is never called here except to
/// void, inside the transaction that holds the lock.
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    /// The code of a document the provider issued with another total than ours.
    static final String TOTAL_MISMATCH = "provider-total-mismatch";
    /// How long after an attempt started a discard still waits for it, beyond the provider timeout.
    private static final java.time.Duration IN_FLIGHT_GRACE = java.time.Duration.ofSeconds(30);

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final InvoiceQueries queries;
    private final WorkOrderBilling workOrders;
    private final CustomerManagement customers;
    private final WorkshopDirectory directory;
    private final InvoicingProvider provider;
    private final PaymentSettling settling;
    private final BillingProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    InvoiceService(InvoiceRepository invoices, PaymentRepository payments, InvoiceQueries queries,
            WorkOrderBilling workOrders, CustomerManagement customers, WorkshopDirectory directory,
            InvoicingProvider provider, PaymentSettling settling, BillingProperties properties,
            ApplicationEventPublisher events, Clock clock) {
        this.invoices = invoices;
        this.payments = payments;
        this.queries = queries;
        this.workOrders = workOrders;
        this.customers = customers;
        this.directory = directory;
        this.provider = provider;
        this.settling = settling;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /// The document a request id belongs to, whatever its status.
    @Transactional(readOnly = true)
    public Optional<Invoice> byRequestId(UUID requestId) {
        return invoices.findByRequestId(requestId);
    }

    /// The invoice of the work order that is PENDING, ISSUED or FAILED.
    @Transactional(readOnly = true)
    public Optional<Invoice> activeInvoice(UUID workOrderId) {
        return invoices.findActiveInvoice(workOrderId);
    }

    /// Stores the invoice of a delivered work order as PENDING: its lines and totals as they are now, the workshop and
    /// the customer as they are now. The provider has not been asked yet.
    ///
    /// @throws ProviderNotConfiguredException                    when the invoicing provider is none
    /// @throws BillingWorkOrderNotFoundException                 for an unknown work order
    /// @throws WorkOrderNotInvoiceableException                  unless the order is DELIVERED and has lines
    /// @throws com.asp.platform.billing.domain.ActiveInvoiceRaceException when another request stored an invoice first
    @Transactional
    public Invoice open(CurrentUser user, UUID workOrderId, IssueRequest request) {
        if (!provider.configured()) {
            throw new ProviderNotConfiguredException();
        }
        BillableWorkOrder order = workOrders.billable(workOrderId).orElseThrow(BillingWorkOrderNotFoundException::new);
        if (!order.delivered() || order.lines().isEmpty()) {
            throw new WorkOrderNotInvoiceableException(order.status());
        }
        List<InvoiceLine> lines = order.lines().stream()
                .map(l -> new InvoiceLine(UUID.randomUUID(), l.position(), LineKind.valueOf(l.type()), l.description(),
                        l.quantity(), l.unitPriceCents(), l.discountPercent(), l.vatRate(), l.netCents(), l.vatCents(),
                        l.sourceLineId()))
                .toList();
        WorkshopFiscalProfile workshop = directory.fiscalProfile(user.tenantId());
        long total = InvoiceTotals.of(lines).totalCents();
        FiscalRules.Party owner = customers.fiscalDataForVehicle(order.vehicleId()).map(InvoiceService::party)
                .orElse(null);
        var customer = FiscalRules.customerFor(request.finalConsumer(), request.customer(), owner, workshop.country(),
                total, properties.finalConsumerLimitCents());
        String plate = customers.findVehicle(order.vehicleId()).map(VehicleSummary::plateDisplay).orElse(null);
        Invoice invoice = Invoice.open(UUID.randomUUID(), DocumentKind.INVOICE, order.id(), order.number(), null,
                request.requestId(), plate, new IssuerSnapshot(workshop.name(), workshop.nif(), workshop.country()),
                customer, lines, null, provider.id(), user.userId(), clock.instant());
        return invoices.save(invoice);
    }

    /// Stores a full credit note of an issued invoice as PENDING, or finds the one this request already made.
    ///
    /// @throws InvoiceNotFoundException   for an unknown invoice
    /// @throws CreditNoteExistsException  when the invoice has an active credit note from another request
    /// @throws InvoiceNotIssuedException  unless the invoice is ISSUED (a credit note cannot be credited)
    @Transactional
    public Opened openCreditNote(CurrentUser user, UUID invoiceId, UUID requestId, String reason) {
        if (!provider.configured()) {
            throw new ProviderNotConfiguredException();
        }
        Invoice invoice = lock(invoiceId);
        Optional<Invoice> existing = invoices.findActiveCreditNote(invoiceId);
        if (existing.isPresent()) {
            if (existing.get().requestId().equals(requestId)) {
                return new Opened(existing.get(), true);
            }
            throw new CreditNoteExistsException();
        }
        if (invoices.findByRequestId(requestId).isPresent()) {
            throw new RequestIdReusedException();
        }
        invoice.requireIssuedInvoice();
        Invoice note = invoice.creditNote(UUID.randomUUID(), requestId, reason, provider.id(), UUID::randomUUID,
                user.userId(), clock.instant());
        return new Opened(invoices.save(note), false);
    }

    /// @param replay true when the credit note was found, not created
    public record Opened(Invoice note, boolean replay) {
    }

    /// What the provider is asked to issue for this document; null when it is already ISSUED. A credit note names the
    /// invoice it credits.
    ///
    /// @throws InvoiceNotFoundException for an unknown document
    /// @param mustAwaitProvider true for a staff retry: anything but PENDING or FAILED is refused, ISSUED included
    /// @throws com.asp.platform.billing.domain.InvoiceNotRetryableException when it is VOIDED or DISCARDED (or ISSUED, if asked)
    @Transactional(readOnly = true)
    public Prepared prepare(UUID id, boolean mustAwaitProvider) {
        Invoice invoice = invoices.findById(id).orElseThrow(InvoiceNotFoundException::new);
        if (!mustAwaitProvider && invoice.status() == InvoiceStatus.ISSUED) {
            return new Prepared(invoice, null);
        }
        invoice.requireRetryable();
        DocumentDraft.Credited credited = null;
        if (invoice.creditOfId() != null) {
            Invoice original = invoices.findById(invoice.creditOfId()).orElseThrow(InvoiceNotFoundException::new);
            IssuedDocument issued = original.issued();
            if (issued == null) {
                throw new InvoiceNotIssuedException();
            }
            credited = new DocumentDraft.Credited(issued.providerDocumentId(), issued.fullNumber());
        }
        return new Prepared(invoice, invoice.draft(credited));
    }

    public record Prepared(Invoice invoice, @Nullable DocumentDraft draft) {
    }

    /// Marks that a call to the provider starts: a short transaction that commits before the call, so a discard in the
    /// meantime sees it and waits (409 `try-again`) instead of freeing the work order for a second invoice.
    ///
    /// @return false when another call for this document is already under way: do not send a second one
    @Transactional
    public boolean beginAttempt(UUID id) {
        Invoice invoice = lock(id);
        if (invoice.status() == InvoiceStatus.ISSUED) {
            return true;
        }
        Instant now = clock.instant();
        if (invoice.attemptInFlight(now, properties.providerTimeout().plus(IN_FLIGHT_GRACE))) {
            return false;
        }
        invoice.markAttemptStarted(now);
        invoices.save(invoice);
        return true;
    }

    /// The provider issued the document. A credit note also cancels the credited invoice's pending payments. Does
    /// nothing when the document is already ISSUED (two callers raced). A total that differs from ours is not
    /// accepted: the document is FAILED with `provider-total-mismatch`, visible to staff. A document discarded while
    /// the provider was issuing it keeps the number and is flagged (see [Invoice#markIssued]).
    @Transactional
    public void recordIssued(UUID tenantId, UUID id, IssuedDocument document) {
        Invoice invoice = lock(id);
        Instant now = clock.instant();
        if (invoice.status().awaitsProvider() && document.totalCents() != invoice.totalCents()) {
            log.warn("Provider issued another total invoice={} number={} ours={} theirs={}", id,
                    document.fullNumber(), invoice.totalCents(),
                    document.totalCents());
            invoice.markRejected(TOTAL_MISMATCH, "The provider issued " + document.fullNumber() + " ("
                    + document.providerDocumentId() + ") with a total of " + document.totalCents()
                    + " cents, expected " + invoice.totalCents(), now);
            invoices.save(invoice);
            return;
        }
        if (!invoice.markIssued(document, now)) {
            return;
        }
        Invoice saved = invoices.save(invoice);
        if (saved.status() == InvoiceStatus.DISCARDED) {
            log.warn("Provider issued a discarded document invoice={} workOrder={} number={}", id,
                    saved.workOrderId(), document.fullNumber());
            return;
        }
        if (saved.kind() == DocumentKind.CREDIT_NOTE && saved.creditOfId() != null) {
            invoices.lockById(saved.creditOfId());
            settling.cancelPending(payments.lockByInvoice(saved.creditOfId()));
        }
        events.publishEvent(new InvoiceIssued(tenantId, saved.id(), saved.workOrderId(), document.fullNumber(),
                saved.totalCents()));
    }

    /// The provider refused the document: FAILED until someone retries or discards it.
    @Transactional
    public void recordRejected(UUID id, String code, String message) {
        Invoice invoice = lock(id);
        if (!invoice.status().awaitsProvider()) {
            return;
        }
        invoice.markRejected(code, message, clock.instant());
        invoices.save(invoice);
    }

    /// The provider did not answer: the document waits as PENDING.
    @Transactional
    public void recordUnavailable(UUID id, String message) {
        Invoice invoice = lock(id);
        if (!invoice.status().awaitsProvider()) {
            return;
        }
        invoice.markUnavailable(message, clock.instant());
        invoices.save(invoice);
    }

    /// Gives up on a failed document, which frees the work order (or the invoice) for a new one.
    ///
    /// @throws com.asp.platform.shared.StaleDataException                       for another stored version
    /// @throws com.asp.platform.billing.domain.InvoiceNotDiscardableException   unless FAILED
    @Transactional
    public InvoiceView discard(CurrentUser user, UUID id, String reason, long expectedVersion) {
        Invoice invoice = lock(id);
        invoice.requireVersion(expectedVersion);
        Instant now = clock.instant();
        if (invoice.attemptInFlight(now, properties.providerTimeout().plus(IN_FLIGHT_GRACE))) {
            // The provider may be issuing it right now: wait for the answer, then decide.
            throw new ActiveInvoiceRaceException();
        }
        invoice.discard(reason.strip(), user.userId(), now);
        return view(invoices.save(invoice));
    }

    /// Annuls an issued invoice at the provider and cancels its pending payments. A voided invoice answers again
    /// without doing anything (safe to repeat). The provider is called inside this transaction, holding the lock.
    ///
    /// @throws com.asp.platform.shared.StaleDataException  for another stored version
    /// @throws InvoiceNotVoidableException                 unless an ISSUED invoice without a credit note
    /// @throws InvoiceHasPaymentsException                 when a payment was received
    /// @throws ProviderRejectedConflictException           when the provider refuses (nothing changes)
    /// @throws com.asp.platform.billing.domain.ProviderUnavailableException when it cannot be reached (nothing changes)
    @Transactional
    public InvoiceView voidInvoice(CurrentUser user, UUID id, String reason, long expectedVersion) {
        Invoice invoice = lock(id);
        if (invoice.kind() == DocumentKind.INVOICE && invoice.status() == InvoiceStatus.VOIDED) {
            return view(invoice);
        }
        invoice.requireVersion(expectedVersion);
        invoice.requireVoidable();
        if (invoices.findActiveCreditNote(id).isPresent()) {
            throw new InvoiceNotVoidableException();
        }
        List<Payment> existing = payments.lockByInvoice(id);
        if (existing.stream().anyMatch(p -> p.status() == PaymentStatus.PAID)) {
            throw new InvoiceHasPaymentsException();
        }
        IssuedDocument issued = invoice.issued();
        if (issued == null) {
            throw new InvoiceNotVoidableException();
        }
        try {
            provider.voidDocument(issued.providerDocumentId(), reason.strip());
        } catch (ProviderRejectedException e) {
            throw new ProviderRejectedConflictException();
        }
        invoice.voidIt(reason.strip(), user.userId(), clock.instant());
        Invoice saved = invoices.save(invoice);
        settling.cancelPending(existing);
        return view(saved);
    }

    /// @throws InvoiceNotFoundException for an unknown document
    @Transactional(readOnly = true)
    public InvoiceView get(UUID id) {
        return view(invoices.findById(id).orElseThrow(InvoiceNotFoundException::new));
    }

    /// The documents of a work order, newest first.
    ///
    /// @throws BillingWorkOrderNotFoundException for an unknown work order
    @Transactional(readOnly = true)
    public List<InvoiceView> ofWorkOrder(UUID workOrderId) {
        workOrders.billable(workOrderId).orElseThrow(BillingWorkOrderNotFoundException::new);
        return invoices.findByWorkOrder(workOrderId).stream().map(this::view).toList();
    }

    /// The PDF from the provider, for an ISSUED or VOIDED document.
    ///
    /// @throws InvoiceNotIssuedException for any other status
    @Transactional(readOnly = true)
    public Pdf pdf(UUID id) {
        Invoice invoice = invoices.findById(id).orElseThrow(InvoiceNotFoundException::new);
        IssuedDocument issued = invoice.issued();
        if (issued == null || (invoice.status() != InvoiceStatus.ISSUED && invoice.status() != InvoiceStatus.VOIDED)) {
            throw new InvoiceNotIssuedException();
        }
        if (!provider.configured() || !provider.id().equals(invoice.provider())) {
            // The PDF comes from the provider that issued the document, never from another one.
            throw new ProviderNotConfiguredException();
        }
        return new Pdf(issued.fullNumber(), provider.pdf(invoice));
    }

    public record Pdf(String number, byte[] content) {
    }

    /// The list, newest first.
    ///
    /// @param from inclusive day in the workshop time zone, or null
    /// @param to   inclusive day in the workshop time zone, or null
    @Transactional(readOnly = true)
    public InvoiceQueries.Page list(UUID tenantId, List<InvoiceStatus> statuses, @Nullable DocumentKind kind,
            @Nullable PaymentProgress progress, @Nullable LocalDate from,
            @Nullable LocalDate to, @Nullable String search, int page, int size) {
        ZoneId zone = directory.timezone(tenantId);
        Instant start = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return queries.find(new InvoiceQueries.Filter(statuses, kind, progress, start, end, search), page, size);
    }

    /// PENDING documents the job may retry.
    @Transactional(readOnly = true)
    public List<UUID> retryable(Instant lastAttemptBefore, int maxAttempts, int limit) {
        return invoices.findRetryable(lastAttemptBefore, maxAttempts, limit);
    }

    InvoiceView view(Invoice invoice) {
        List<Payment> paid = invoice.kind() == DocumentKind.INVOICE ? payments.findByInvoice(invoice.id()) : List.of();
        Invoice creditNote = invoice.kind() == DocumentKind.INVOICE
                ? invoices.findActiveCreditNote(invoice.id()).orElse(null) : null;
        String creditOfNumber = invoice.creditOfId() == null ? null
                : invoices.findById(invoice.creditOfId()).map(Invoice::issued).map(IssuedDocument::fullNumber)
                        .orElse(null);
        boolean credited = creditNote != null && creditNote.status() == InvoiceStatus.ISSUED;
        return new InvoiceView(invoice, paid, creditNote, creditOfNumber, Settlement.of(invoice, paid, credited));
    }

    private Invoice lock(UUID id) {
        return invoices.lockById(id).orElseThrow(InvoiceNotFoundException::new);
    }

    private static FiscalRules.Party party(CustomerFiscalData data) {
        CustomerFiscalData.FiscalAddress a = data.address();
        FiscalRules.PartyAddress address = a == null ? null
                : new FiscalRules.PartyAddress(a.street(), a.detail(), a.postalCode(), a.locality(), a.country());
        return new FiscalRules.Party(data.name(), data.nif(), address);
    }
}
