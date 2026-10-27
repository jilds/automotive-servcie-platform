package com.asp.platform.stock.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.stock.StockReservations;
import com.asp.platform.stock.domain.MovementType;
import com.asp.platform.stock.domain.Product;
import com.asp.platform.stock.domain.ProductDetails;
import com.asp.platform.stock.domain.ProductNotFoundException;
import com.asp.platform.stock.domain.ProductRepository;
import com.asp.platform.stock.domain.PurchaseOrder;
import com.asp.platform.stock.domain.PurchaseOrderDetails;
import com.asp.platform.stock.domain.PurchaseOrderLine;
import com.asp.platform.stock.domain.PurchaseOrderNotFoundException;
import com.asp.platform.stock.domain.PurchaseOrderRepository;
import com.asp.platform.stock.domain.PurchaseOrderStatus;
import com.asp.platform.stock.domain.StockMovement;
import com.asp.platform.stock.domain.StockMovementRepository;
import com.asp.platform.stock.domain.StockReference;

/// Purchase orders: prepared as a draft, sent to the supplier, and received, which adds every line to stock with a
/// RECEIPT movement.
@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository orders;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final Clock clock;

    PurchaseOrderService(PurchaseOrderRepository orders, ProductRepository products, StockMovementRepository movements,
            Clock clock) {
        this.orders = orders;
        this.products = products;
        this.movements = movements;
        this.clock = clock;
    }

    /// Newest first; null lists every status.
    @Transactional(readOnly = true)
    public List<PurchaseOrderView> list(@Nullable PurchaseOrderStatus status) {
        List<PurchaseOrder> found = orders.list(status);
        Map<UUID, Product> byId = productsOf(found.stream()
                .flatMap(o -> o.details().lines().stream()).map(PurchaseOrderLine::productId)
                .collect(Collectors.toSet()));
        return found.stream().map(o -> view(o, byId)).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderView get(UUID id) {
        return view(order(id));
    }

    /// A new draft. Its products must belong to the workshop and still be sold.
    @Transactional
    public PurchaseOrderView create(PurchaseOrderDetails details) {
        requireProducts(details, Set.of());
        return view(orders.save(PurchaseOrder.draft(UUID.randomUUID(), orders.nextNumber(), details)));
    }

    /// Replaces supplier, notes and lines of a draft. Products already on it may have been deactivated since; only
    /// newly added ones must still be sold.
    ///
    /// @throws com.asp.platform.stock.domain.PurchaseOrderLockedException when it was already sent
    /// @throws com.asp.platform.shared.StaleDataException                 when someone else saved it since
    @Transactional
    public PurchaseOrderView update(UUID id, PurchaseOrderDetails details, long expectedVersion) {
        PurchaseOrder order = order(id);
        order.requireVersion(expectedVersion);
        Set<UUID> before = order.details().lines().stream().map(PurchaseOrderLine::productId)
                .collect(Collectors.toSet());
        requireProducts(details, before);
        order.update(details);
        return view(orders.save(order));
    }

    /// The draft was sent to the supplier; it can no longer be changed.
    @Transactional
    public PurchaseOrderView markOrdered(UUID id) {
        PurchaseOrder order = order(id);
        order.markOrdered(clock.instant());
        return view(orders.save(order));
    }

    /// The parts arrived: every line becomes a RECEIPT movement referencing the order. The order is saved first, so
    /// a second receipt of the same order at the same time fails on its version before any stock moves. Products
    /// are locked in id order, so two receipts cannot deadlock.
    ///
    /// @param userId who received the parts
    /// @throws com.asp.platform.stock.domain.IllegalPurchaseOrderStatusException when it was not ordered, or was
    ///                                                                           already received
    @Transactional
    public PurchaseOrderView receive(UUID id, UUID userId) {
        PurchaseOrder order = order(id);
        Instant now = clock.instant();
        order.markReceived(now);
        PurchaseOrder saved = orders.save(order);
        StockReference reference = new StockReference(StockReference.PURCHASE_ORDER, id);
        Map<UUID, BigDecimal> perProduct = new TreeMap<>(StockReservations.LOCK_ORDER);
        saved.details().lines().forEach(line -> perProduct.merge(line.productId(), line.quantity(), BigDecimal::add));
        perProduct.forEach((productId, quantity) -> {
            Product product = products.lock(productId).orElseThrow(ProductNotFoundException::new);
            StockMovement movement = product.record(UUID.randomUUID(), MovementType.RECEIPT, quantity, reference, null,
                    userId, now);
            products.save(product);
            movements.add(movement);
        });
        return view(saved);
    }

    private PurchaseOrder order(UUID id) {
        return orders.findById(id).orElseThrow(PurchaseOrderNotFoundException::new);
    }

    /// @param known products already on the order, which may be inactive
    /// @throws ProductNotFoundException when a product is not in the workshop
    /// @throws com.asp.platform.stock.domain.ProductInactiveException when a new product is no longer sold
    private void requireProducts(PurchaseOrderDetails details, Set<UUID> known) {
        Set<UUID> ids = details.lines().stream().map(PurchaseOrderLine::productId).collect(Collectors.toSet());
        Map<UUID, Product> byId = productsOf(ids);
        if (!byId.keySet().containsAll(ids)) {
            throw new ProductNotFoundException();
        }
        ids.stream().filter(productId -> !known.contains(productId))
                .forEach(productId -> Objects.requireNonNull(byId.get(productId)).requireActive());
    }

    private PurchaseOrderView view(PurchaseOrder order) {
        return view(order, productsOf(order.details().lines().stream().map(PurchaseOrderLine::productId)
                .collect(Collectors.toSet())));
    }

    private static PurchaseOrderView view(PurchaseOrder order, Map<UUID, Product> byId) {
        List<PurchaseOrderView.Line> lines = order.details().lines().stream()
                .filter(line -> byId.containsKey(line.productId()))
                .map(line -> {
                    ProductDetails d = Objects.requireNonNull(byId.get(line.productId())).details();
                    return new PurchaseOrderView.Line(line.productId(), d.sku(), d.name(), d.unit(), line.quantity(),
                            line.unitCostCents(), line.totalCents());
                })
                .toList();
        return new PurchaseOrderView(order, lines);
    }

    private Map<UUID, Product> productsOf(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return products.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::id, Function.identity(), (a, b) -> a));
    }
}
