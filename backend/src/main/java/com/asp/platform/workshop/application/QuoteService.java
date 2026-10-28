package com.asp.platform.workshop.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.customer.CustomerContact;
import com.asp.platform.customer.CustomerManagement;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.CurrentUser;
import com.asp.platform.shared.StatusTokens;
import com.asp.platform.workshop.QuoteSent;
import com.asp.platform.workshop.domain.Quote;
import com.asp.platform.workshop.domain.QuoteDecision;
import com.asp.platform.workshop.domain.QuoteExpiredException;
import com.asp.platform.workshop.domain.QuoteNotFoundException;
import com.asp.platform.workshop.domain.QuoteRepository;
import com.asp.platform.workshop.domain.QuoteStatus;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderNotFoundException;
import com.asp.platform.workshop.domain.WorkOrderRepository;
import com.asp.platform.workshop.domain.WorkOrderStatus;

/// Quotes of a work order: sending, the front desk's list and decisions, the customer's link and the expiry.
///
/// Every change locks the work order row first ([WorkOrderRepository#lockById], 3 s → 409 `try-again`) and reads
/// the quote after the lock, as line changes in [WorkOrderService] do. So an approval and a line change of the same
/// order run one after the other: either the quote is approved with the old lines and the line change then finds no
/// SENT quote, or the line change supersedes it first and the approval answers 409 `quote-not-open`.
///
/// A decision on a SENT quote past its validity stores it as EXPIRED and answers 409 `quote-expired`: those methods
/// commit on that exception (`noRollbackFor`), having changed nothing else.
@Service
public class QuoteService {

    private final QuoteRepository quotes;
    private final WorkOrderRepository orders;
    private final WorkshopDirectory directory;
    private final CustomerManagement customers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    QuoteService(QuoteRepository quotes, WorkOrderRepository orders, WorkshopDirectory directory,
            CustomerManagement customers, ApplicationEventPublisher events, Clock clock) {
        this.quotes = quotes;
        this.orders = orders;
        this.directory = directory;
        this.customers = customers;
        this.events = events;
        this.clock = clock;
    }

    /// Sends a quote of the order's current lines and totals, valid for the workshop's `quoteValidDays`: a SENT quote
    /// of the order becomes SUPERSEDED and the order moves to AWAITING_APPROVAL. The customer's e-mail, when there is
    /// one, gets the link after the commit.
    ///
    /// @throws WorkOrderNotFoundException                                         for an unknown order
    /// @throws com.asp.platform.workshop.domain.WorkOrderNotQuotableException    outside DIAGNOSTICS /
    ///                                                                            AWAITING_APPROVAL or without lines
    @Transactional
    public SentQuote send(CurrentUser user, UUID workOrderId) {
        WorkOrder order = lockedOrder(workOrderId);
        order.requireQuotable();
        Instant now = clock.instant();
        supersedeSent(order.id(), now);
        String token = StatusTokens.generate();
        Quote saved = quotes.save(Quote.send(UUID.randomUUID(), quotes.nextNumber(), order, user.userId(),
                StatusTokens.hash(token), now, directory.quoteValidDays(user.tenantId()), UUID::randomUUID));
        if (order.status() == WorkOrderStatus.DIAGNOSTICS) {
            order.moveTo(WorkOrderStatus.AWAITING_APPROVAL, now);
            orders.save(order);
        }
        Optional<CustomerContact> contact = customers.contactForVehicle(order.vehicleId());
        String email = contact.map(CustomerContact::email).orElse(null);
        if (email != null) {
            CustomerContact to = contact.orElseThrow();
            events.publishEvent(new QuoteSent(user.tenantId(), saved.number(), order.number(), to.name(), email,
                    to.plateDisplay(), saved.totalCents(), saved.validUntil(), token));
        }
        return new SentQuote(view(saved, directory.memberNames(user.tenantId()), now), token, email != null);
    }

    /// The order's quotes with their lines, newest first.
    ///
    /// @throws WorkOrderNotFoundException for an unknown order
    @Transactional(readOnly = true)
    public List<QuoteView> list(UUID tenantId, UUID workOrderId) {
        orders.findById(workOrderId).orElseThrow(WorkOrderNotFoundException::new);
        List<Quote> found = quotes.findByWorkOrder(workOrderId);
        Map<UUID, String> names = found.isEmpty() ? Map.of() : directory.memberNames(tenantId);
        Instant now = clock.instant();
        return found.stream().map(q -> view(q, names, now)).toList();
    }

    /// The front desk or owner records the customer's decision with a note. An approval moves the order as on the
    /// link.
    ///
    /// @throws QuoteNotFoundException                                         for a quote of another order
    /// @throws com.asp.platform.shared.StaleDataException                     for another stored version
    /// @throws com.asp.platform.workshop.domain.DecisionNoteRequiredException without a note
    /// @throws com.asp.platform.workshop.domain.QuoteNotOpenException         when the quote is not SENT
    /// @throws QuoteExpiredException                                          when it is past its validity
    @Transactional(noRollbackFor = QuoteExpiredException.class)
    public QuoteView decide(CurrentUser user, UUID workOrderId, UUID quoteId, QuoteDecision decision,
            @Nullable String note, long expectedVersion) {
        WorkOrder order = lockedOrder(workOrderId);
        Quote quote = quotes.findById(quoteId).filter(q -> q.workOrderId().equals(workOrderId))
                .orElseThrow(QuoteNotFoundException::new);
        quote.requireVersion(expectedVersion);
        Instant now = clock.instant();
        expireIfDue(quote, now);
        quote.decideByStaff(decision, note, user.userId(), now);
        Quote saved = quotes.save(quote);
        moveOnApproval(order, saved, now);
        return view(saved, directory.memberNames(user.tenantId()), now);
    }

    /// The quote behind a link, for the customer.
    ///
    /// @throws QuoteNotFoundException for an unknown token
    @Transactional(readOnly = true)
    public PublicQuoteView publicView(String token, String workshopName, ZoneId zone) {
        Quote quote = quotes.findByTokenHash(StatusTokens.hash(token)).orElseThrow(QuoteNotFoundException::new);
        WorkOrder order = orders.findById(quote.workOrderId()).orElseThrow(QuoteNotFoundException::new);
        return publicView(quote, order, workshopName, zone, clock.instant());
    }

    /// The customer approves on the link with their name; the order moves on when it is still awaiting approval.
    @Transactional(noRollbackFor = QuoteExpiredException.class)
    public PublicQuoteView approve(String token, String name, String workshopName, ZoneId zone) {
        return decideOnLink(token, workshopName, zone, (quote, now) -> quote.approveByCustomer(name, now));
    }

    /// The customer rejects on the link, with an optional reason; the order stays where it is.
    @Transactional(noRollbackFor = QuoteExpiredException.class)
    public PublicQuoteView reject(String token, @Nullable String reason, String workshopName, ZoneId zone) {
        return decideOnLink(token, workshopName, zone, (quote, now) -> quote.rejectByCustomer(reason, now));
    }

    /// Ids of the current workshop's SENT quotes past their validity, earliest first. Run it inside the workshop's
    /// [com.asp.platform.shared.TenantScope] (see [QuoteExpiry]).
    @Transactional(readOnly = true)
    public List<UUID> dueIds(Instant now, int limit) {
        return quotes.findDueIds(now, limit);
    }

    /// Stores one quote past its validity as EXPIRED, in its own transaction, after locking its work order. The
    /// order does not move. Run it inside the workshop's [com.asp.platform.shared.TenantScope].
    ///
    /// @return false when the quote is gone or no longer due
    @Transactional
    public boolean expire(UUID quoteId, Instant now) {
        Optional<UUID> workOrderId = quotes.findWorkOrderId(quoteId);
        if (workOrderId.isEmpty()) {
            return false;
        }
        lockedOrder(workOrderId.get());
        Optional<Quote> quote = quotes.findById(quoteId);
        if (quote.isEmpty() || !quote.get().isDue(now)) {
            return false;
        }
        quote.get().expire(now);
        quotes.save(quote.get());
        return true;
    }

    /// The order's SENT quote, if any, becomes SUPERSEDED (or EXPIRED when already past its validity). Runs inside
    /// the caller's transaction, which holds the order's lock ([WorkOrderService] on line changes and cancel).
    @Transactional(propagation = Propagation.MANDATORY)
    public void supersedeOpen(UUID workOrderId) {
        supersedeSent(workOrderId, clock.instant());
    }

    private void supersedeSent(UUID workOrderId, Instant now) {
        quotes.findSent(workOrderId).ifPresent(sent -> {
            sent.supersede(now);
            quotes.save(sent);
        });
    }

    private interface LinkDecision {
        void apply(Quote quote, Instant now);
    }

    private PublicQuoteView decideOnLink(String token, String workshopName, ZoneId zone, LinkDecision decision) {
        String hash = StatusTokens.hash(token);
        UUID workOrderId = quotes.findWorkOrderIdByTokenHash(hash).orElseThrow(QuoteNotFoundException::new);
        WorkOrder order = orders.lockById(workOrderId).orElseThrow(QuoteNotFoundException::new);
        Quote quote = quotes.findByTokenHash(hash).orElseThrow(QuoteNotFoundException::new);
        Instant now = clock.instant();
        expireIfDue(quote, now);
        decision.apply(quote, now);
        Quote saved = quotes.save(quote);
        moveOnApproval(order, saved, now);
        return publicView(saved, order, workshopName, zone, now);
    }

    /// Stores a SENT quote past its validity as EXPIRED and refuses the decision.
    private void expireIfDue(Quote quote, Instant now) {
        if (quote.isDue(now)) {
            quote.expire(now);
            quotes.save(quote);
            throw new QuoteExpiredException();
        }
    }

    /// An approval moves an order still awaiting approval: to AWAITING_PARTS when a part was typed by hand (it
    /// probably has to be ordered), else to IN_PROGRESS. In another status (moved by hand), only the decision counts.
    private void moveOnApproval(WorkOrder order, Quote quote, Instant now) {
        if (quote.status() == QuoteStatus.APPROVED && order.status() == WorkOrderStatus.AWAITING_APPROVAL) {
            order.moveTo(quote.hasPartsToOrder() ? WorkOrderStatus.AWAITING_PARTS : WorkOrderStatus.IN_PROGRESS, now);
            orders.save(order);
        }
    }

    private PublicQuoteView publicView(Quote quote, WorkOrder order, String workshopName, ZoneId zone, Instant now) {
        PublicQuoteView.Vehicle vehicle = customers.findVehicle(order.vehicleId())
                .map(v -> new PublicQuoteView.Vehicle(v.plateDisplay(), v.plateEra(), v.registrationCountry(),
                        v.label()))
                .orElse(null);
        return new PublicQuoteView(quote, quote.statusAt(now), order.number(), vehicle, workshopName, zone);
    }

    private WorkOrder lockedOrder(UUID id) {
        return orders.lockById(id).orElseThrow(WorkOrderNotFoundException::new);
    }

    private static QuoteView view(Quote quote, Map<UUID, String> names, Instant now) {
        UUID decidedBy = quote.decidedBy();
        return new QuoteView(quote, quote.statusAt(now), names.get(quote.sentBy()),
                decidedBy == null ? null : names.get(decidedBy));
    }
}
