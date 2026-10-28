package com.asp.platform.workshop.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.booking.BookingCheckIns;
import com.asp.platform.catalogue.CatalogueLine;
import com.asp.platform.catalogue.CatalogueLineType;
import com.asp.platform.catalogue.CatalogueLookup;
import com.asp.platform.customer.CustomerManagement;
import com.asp.platform.customer.VehicleSummary;
import com.asp.platform.identity.TeamMember;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.CurrentUser;
import com.asp.platform.stock.ReservationSource;
import com.asp.platform.stock.StockItem;
import com.asp.platform.stock.StockReservations;
import com.asp.platform.workshop.domain.CheckInDetails;
import com.asp.platform.workshop.domain.InvalidMechanicException;
import com.asp.platform.workshop.domain.LineType;
import com.asp.platform.workshop.domain.MechanicChange;
import com.asp.platform.workshop.domain.MechanicChangeRepository;
import com.asp.platform.workshop.domain.NewLine;
import com.asp.platform.workshop.domain.QuoteHeadline;
import com.asp.platform.workshop.domain.QuoteRepository;
import com.asp.platform.workshop.domain.UnknownVehicleException;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderLine;
import com.asp.platform.workshop.domain.WorkOrderNotFoundException;
import com.asp.platform.workshop.domain.WorkOrderRepository;
import com.asp.platform.workshop.domain.WorkOrderStatus;

@Service
public class WorkOrderService {

    /// Moves a mechanic may make on their own work orders.
    private static final Set<WorkOrderStatus> MECHANIC_MOVES = EnumSet.of(WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.AWAITING_PARTS, WorkOrderStatus.READY);

    /// Request fields that carry the mechanic id, reported back when the mechanic is not valid.
    private static final String CHECK_IN_MECHANIC_FIELD = "assignedMechanicId";
    private static final String ASSIGN_MECHANIC_FIELD = "mechanicId";

    private final WorkOrderRepository orders;
    private final MechanicChangeRepository mechanicChanges;
    private final QuoteRepository quotes;
    private final QuoteService quoteService;
    private final CatalogueLookup catalogue;
    private final StockReservations stock;
    private final CustomerManagement customers;
    private final WorkshopDirectory directory;
    private final BookingCheckIns bookings;
    private final Clock clock;

    WorkOrderService(WorkOrderRepository orders, MechanicChangeRepository mechanicChanges, QuoteRepository quotes,
            QuoteService quoteService, CatalogueLookup catalogue, StockReservations stock,
            CustomerManagement customers, WorkshopDirectory directory, BookingCheckIns bookings, Clock clock) {
        this.orders = orders;
        this.mechanicChanges = mechanicChanges;
        this.quotes = quotes;
        this.quoteService = quoteService;
        this.catalogue = catalogue;
        this.stock = stock;
        this.customers = customers;
        this.directory = directory;
        this.bookings = bookings;
        this.clock = clock;
    }

    /// Opens a work order when the car arrives and records the odometer reading on the vehicle. A mechanic given here
    /// is the first entry of the order's mechanic history.
    /// A mechanic, when given, must be an active mechanic of the workshop.
    ///
    /// From a booking (`bookingId` given), in the same transaction: the booking must be CONFIRMED (409
    /// `booking-not-confirmed`), each booked service still active becomes a catalogue line at today's catalogue
    /// price, and the booking becomes CHECKED_IN with this work order. Any failure undoes all of it.
    @Transactional
    public WorkOrderView checkIn(CurrentUser user, UUID vehicleId, CheckInDetails details, @Nullable UUID mechanicId,
            @Nullable UUID bookingId) {
        requireMechanic(user.tenantId(), mechanicId, CHECK_IN_MECHANIC_FIELD);
        VehicleSummary vehicle = customers.findVehicle(vehicleId)
                .orElseThrow(UnknownVehicleException::new);
        List<UUID> bookedServices = bookingId == null ? List.of() : bookings.claim(bookingId, vehicle.id());
        WorkOrder opened = WorkOrder.open(UUID.randomUUID(), orders.nextNumber(), vehicle.id(), details, mechanicId,
                clock.instant());
        List<CatalogueLine> lines = new ArrayList<>();
        bookedServices.forEach(serviceId -> lines.addAll(catalogue.linesForService(serviceId)));
        WorkOrder order = orders.save(opened);
        if (!lines.isEmpty()) {
            order = addLines(order, lines);
        }
        if (bookingId != null) {
            bookings.markCheckedIn(bookingId, order.id());
        }
        if (mechanicId != null) {
            // The first entry of the mechanic history: who was given the car at check-in.
            mechanicChanges.save(new MechanicChange(UUID.randomUUID(), order.id(), null, mechanicId, null,
                    user.userId(), order.statusChangedAt()));
        }
        if (details.mileageIn() != null) {
            customers.recordMileage(vehicle.id(), details.mileageIn());
        }
        return new WorkOrderView(order, VehiclePlate.of(vehicle), null);
    }

    /// Any work order of the workshop. A mechanic may open jobs that are not theirs (from a vehicle's service
    /// history) but only to read them: prices are hidden for every mechanic and the response is read-only.
    @Transactional(readOnly = true)
    public WorkOrderView get(UUID id) {
        return view(order(id));
    }

    @Transactional
    public WorkOrderView addLine(UUID id, NewLine line) {
        WorkOrder order = lockedOrder(id);
        order.addLine(UUID.randomUUID(), line);
        WorkOrder saved = orders.save(order);
        supersedeQuote(saved);
        return view(saved);
    }

    /// Adds a catalogue service (one line) or a package (one line per item, adding up to the package price) at the
    /// catalogue price. Services become labour lines that remember the service; the products of a package become part
    /// lines that reserve their quantity, as a part line from stock does, all or nothing: when one product has not
    /// enough available nothing is added (409 `insufficient-stock`).
    ///
    /// @param serviceId a service of the catalogue, or null when `packageId` is given
    /// @param packageId a package of the catalogue, or null when `serviceId` is given
    @Transactional
    public WorkOrderView addCatalogueLines(UUID id, @Nullable UUID serviceId, @Nullable UUID packageId) {
        WorkOrder order = lockedOrder(id);
        List<CatalogueLine> lines = serviceId != null
                ? catalogue.linesForService(serviceId)
                : catalogue.linesForPackage(Objects.requireNonNull(packageId, "serviceId or packageId"));
        WorkOrder saved = addLines(order, lines);
        supersedeQuote(saved);
        return view(saved);
    }

    /// Puts catalogue lines on the order and reserves the parts among them, then saves the order.
    private WorkOrder addLines(WorkOrder order, List<CatalogueLine> lines) {
        List<PartReservation> parts = new ArrayList<>();
        for (CatalogueLine line : lines) {
            UUID lineId = UUID.randomUUID();
            UUID productId = line.productId();
            order.addLine(lineId, new NewLine(line.type() == CatalogueLineType.PART ? LineType.PART : LineType.LABOR,
                    line.description(), line.quantity(), line.unitPriceCents(), null, line.vatRate(),
                    line.serviceItemId(), productId));
            if (productId != null) {
                parts.add(new PartReservation(productId, line.quantity(), lineId));
            }
        }
        // Products are locked in one order, so two packages sharing products cannot deadlock.
        parts.stream()
                .sorted(Comparator.comparing(PartReservation::productId, StockReservations.LOCK_ORDER))
                .forEach(part -> stock.reserve(part.productId(), part.quantity(), ReservationSource.WORK_ORDER,
                        order.id(), part.lineId()));
        return orders.save(order);
    }

    /// A part line of a package, to reserve once every line is on the order.
    private record PartReservation(UUID productId, BigDecimal quantity, UUID lineId) {
    }

    /// Adds a part from stock at its sale price and VAT, and reserves the quantity for the line, all or nothing:
    /// when not enough is available nothing is added (409 `insufficient-stock`).
    ///
    /// @throws com.asp.platform.stock.domain.ProductNotFoundException when the workshop has no such product
    /// @throws com.asp.platform.stock.domain.ProductInactiveException when it is no longer sold
    @Transactional
    public WorkOrderView addStockLine(UUID id, UUID productId, BigDecimal quantity) {
        WorkOrder order = lockedOrder(id);
        StockItem item = stock.item(productId);
        UUID lineId = UUID.randomUUID();
        order.addLine(lineId, new NewLine(LineType.PART, item.name() + " (" + item.sku() + ")", quantity,
                item.salePriceCents(), null, item.vatRate(), null, item.id()));
        stock.reserve(item.id(), quantity, ReservationSource.WORK_ORDER, order.id(), lineId);
        WorkOrder saved = orders.save(order);
        supersedeQuote(saved);
        return view(saved);
    }

    /// Removing a part line from stock gives its reservation back.
    @Transactional
    public WorkOrderView removeLine(UUID id, UUID lineId) {
        WorkOrder order = lockedOrder(id);
        boolean fromStock = order.lines().stream()
                .anyMatch(line -> line.id().equals(lineId) && line.productId() != null);
        order.removeLine(lineId);
        if (fromStock) {
            stock.releaseLine(ReservationSource.WORK_ORDER, lineId);
        }
        WorkOrder saved = orders.save(order);
        supersedeQuote(saved);
        return view(saved);
    }

    /// Delivering a work order takes its reserved parts out of stock; cancelling it gives them back.
    @Transactional
    public WorkOrderView move(UUID id, WorkOrderStatus target, CurrentUser user) {
        WorkOrder order = lockedOrder(id);
        if (user.isMechanic() && !order.isAssignedTo(user.userId())) {
            throw new AccessDeniedException("Mechanics can only move their own work orders");
        }
        if (user.isMechanic() && !MECHANIC_MOVES.contains(target)) {
            throw new AccessDeniedException("Mechanics cannot move a work order to " + target);
        }
        order.moveTo(target, clock.instant());
        if (order.lines().stream().map(WorkOrderLine::productId).anyMatch(Objects::nonNull)) {
            if (target == WorkOrderStatus.DELIVERED) {
                stock.consumeAll(ReservationSource.WORK_ORDER, order.id(), user.userId());
            } else if (target == WorkOrderStatus.CANCELLED) {
                stock.releaseAll(ReservationSource.WORK_ORDER, order.id());
            }
        }
        WorkOrder saved = orders.save(order);
        if (target == WorkOrderStatus.CANCELLED) {
            supersedeQuote(saved);
        }
        return view(saved);
    }

    /// Corrects the check-in data while the order is still editable. A changed odometer reading is also
    /// recorded on the vehicle, which keeps the highest reading it has seen.
    @Transactional
    public WorkOrderView editCheckIn(UUID id, CheckInDetails corrected) {
        WorkOrder order = lockedOrder(id);
        @Nullable Integer previousMileage = order.checkIn().mileageIn();
        order.editCheckIn(corrected);
        WorkOrder saved = orders.save(order);
        @Nullable Integer mileage = corrected.mileageIn();
        if (mileage != null && !mileage.equals(previousMileage)) {
            customers.recordMileage(saved.vehicleId(), mileage);
        }
        return view(saved);
    }

    /// Hands the work order to an active mechanic of the workshop, or to nobody (null). Replacing or removing an
    /// assigned mechanic needs a reason; every change is recorded with who made it and why.
    ///
    /// @throws com.asp.platform.workshop.domain.ReasonRequiredException  when a reason is needed and missing
    /// @throws com.asp.platform.workshop.domain.WorkOrderLockedException when the order is delivered or cancelled
    @Transactional
    public WorkOrderView assign(CurrentUser user, UUID id, @Nullable UUID mechanicId, @Nullable String reason) {
        requireMechanic(user.tenantId(), mechanicId, ASSIGN_MECHANIC_FIELD);
        WorkOrder order = lockedOrder(id);
        order.reassign(mechanicId, reason, user.userId(), UUID.randomUUID(), clock.instant())
                .ifPresent(mechanicChanges::save);
        return view(orders.save(order));
    }

    /// The mechanic a work order is assigned to now, and its history of changes with reasons and names.
    @Transactional(readOnly = true)
    public MechanicAssignment mechanicAssignment(UUID tenantId, UUID id) {
        WorkOrder order = order(id);
        Map<UUID, String> names = directory.memberNames(tenantId);
        List<MechanicAssignment.Change> changes = mechanicChanges.findByWorkOrder(id).stream()
                .map(c -> change(c, names))
                .toList();
        UUID current = order.assignedMechanicId();
        return new MechanicAssignment(current, current == null ? null : names.get(current), changes);
    }

    /// Active mechanics a work order can be given to, by name.
    @Transactional(readOnly = true)
    public List<TeamMember> mechanics(UUID tenantId) {
        return directory.activeMechanics(tenantId);
    }

    private static MechanicAssignment.Change change(MechanicChange c, Map<UUID, String> names) {
        return new MechanicAssignment.Change(c.fromMechanicId(), nameOf(c.fromMechanicId(), names), c.toMechanicId(),
                nameOf(c.toMechanicId(), names), c.reason(), c.changedBy(), names.get(c.changedBy()), c.changedAt());
    }

    private static @Nullable String nameOf(@Nullable UUID userId, Map<UUID, String> names) {
        return userId == null ? null : names.get(userId);
    }

    /// Mechanics see only the jobs assigned to them. The vehicles of all cards are read in one query.
    @Transactional(readOnly = true)
    public Board board(CurrentUser user) {
        Set<WorkOrderStatus> columns = WorkOrderStatus.boardColumns();
        List<WorkOrder> active = user.isMechanic()
                ? orders.findByMechanicAndStatusIn(user.userId(), columns)
                : orders.findByStatusIn(columns);
        Map<UUID, VehicleSummary> vehicles = active.isEmpty() ? Map.of()
                : customers.findVehicles(active.stream().map(WorkOrder::vehicleId).collect(Collectors.toSet()));
        Map<UUID, QuoteHeadline> latestQuotes = quotes.latestOf(active.stream().map(WorkOrder::id).toList());
        Instant now = clock.instant();
        Map<WorkOrderStatus, List<BoardCard>> board = new LinkedHashMap<>();
        columns.forEach(status -> board.put(status, new ArrayList<>()));
        for (WorkOrder order : active) {
            Optional<VehicleSummary> vehicle = Optional.ofNullable(vehicles.get(order.vehicleId()));
            board.get(order.status()).add(new BoardCard(order.id(), order.number(),
                    vehicle.map(VehiclePlate::of).orElse(null), vehicle.map(VehicleSummary::label).orElse(""),
                    order.assignedMechanicId(), order.statusChangedAt(),
                    Optional.ofNullable(latestQuotes.get(order.id())).map(q -> q.statusAt(now)).orElse(null)));
        }
        return new Board(board);
    }

    /// Service history of one vehicle, newest first: every job, also for mechanics (who get no prices and can open
    /// other mechanics' jobs read-only). The board stays limited to their own jobs.
    @Transactional(readOnly = true)
    public List<WorkOrderView> historyOf(UUID vehicleId) {
        VehiclePlate plate = plateOf(vehicleId);
        List<WorkOrder> found = orders.findByVehicle(vehicleId);
        Map<UUID, QuoteHeadline> latestQuotes = quotes.latestOf(found.stream().map(WorkOrder::id).toList());
        Instant now = clock.instant();
        return found.stream()
                .map(order -> new WorkOrderView(order, plate, summary(latestQuotes.get(order.id()), now)))
                .toList();
    }

    /// The user table has no row-level security, so the workshop is checked explicitly.
    private void requireMechanic(UUID tenantId, @Nullable UUID mechanicId, String field) {
        if (mechanicId != null && !directory.isActiveMechanic(tenantId, mechanicId)) {
            throw new InvalidMechanicException(field);
        }
    }

    private WorkOrder order(UUID id) {
        return orders.findById(id).orElseThrow(WorkOrderNotFoundException::new);
    }

    private WorkOrderView view(WorkOrder order) {
        QuoteHeadline latest = quotes.latestOf(List.of(order.id())).get(order.id());
        return new WorkOrderView(order, plateOf(order.vehicleId()), summary(latest, clock.instant()));
    }

    private static @Nullable QuoteSummary summary(@Nullable QuoteHeadline quote, Instant now) {
        return quote == null ? null : QuoteSummary.of(quote, now);
    }

    /// Reads the order and locks its row for the rest of the transaction (3 s, then 409 `try-again`), so a line
    /// change, a status move and a quote decision of one order never interleave.
    private WorkOrder lockedOrder(UUID id) {
        return orders.lockById(id).orElseThrow(WorkOrderNotFoundException::new);
    }

    /// The lines changed or the order was cancelled: a SENT quote no longer matches it. Called under the order's
    /// lock.
    private void supersedeQuote(WorkOrder order) {
        quoteService.supersedeOpen(order.id());
    }

    private @Nullable VehiclePlate plateOf(UUID vehicleId) {
        return customers.findVehicle(vehicleId).map(VehiclePlate::of).orElse(null);
    }
}
