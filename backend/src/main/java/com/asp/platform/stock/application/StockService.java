package com.asp.platform.stock.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.stock.ReservationSource;
import com.asp.platform.stock.StockItem;
import com.asp.platform.stock.StockReservations;
import com.asp.platform.stock.domain.DuplicateSkuException;
import com.asp.platform.stock.domain.MovementType;
import com.asp.platform.stock.domain.Product;
import com.asp.platform.stock.domain.ProductDetails;
import com.asp.platform.stock.domain.ProductNotFoundException;
import com.asp.platform.stock.domain.ProductRepository;
import com.asp.platform.stock.domain.Reservation;
import com.asp.platform.stock.domain.ReservationRepository;
import com.asp.platform.stock.domain.StockLevel;
import com.asp.platform.stock.domain.StockMovement;
import com.asp.platform.stock.domain.StockMovementRepository;
import com.asp.platform.stock.domain.StockReference;

/// The workshop's products and their stock. Row-level security keeps every query inside the signed-in workshop, so
/// a product of another workshop reads as not found.
///
/// The quantity on hand changes only together with a ledger row, in the same transaction, while the product row is
/// locked; reservations are checked under the same lock, so two requests cannot both take the last unit.
@Service
public class StockService implements StockReservations {

    /// Movements shown per product, newest first.
    static final int MOVEMENT_LIMIT = 200;

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final ReservationRepository reservations;
    private final WorkshopDirectory directory;
    private final Clock clock;

    StockService(ProductRepository products, StockMovementRepository movements, ReservationRepository reservations,
            WorkshopDirectory directory, Clock clock) {
        this.products = products;
        this.movements = movements;
        this.reservations = reservations;
        this.directory = directory;
        this.clock = clock;
    }

    /// @param query   part of the SKU, name or brand; null or blank lists everything
    /// @param lowOnly only active products with less available than their minimum stock
    @Transactional(readOnly = true)
    public List<ProductView> products(@Nullable String query, boolean activeOnly, boolean lowOnly) {
        List<Product> found = products.search(blankToNull(query), activeOnly || lowOnly);
        Map<UUID, BigDecimal> reserved = reservations.reservedQuantities(found.stream().map(Product::id).toList());
        return found.stream()
                .map(p -> new ProductView(p, new StockLevel(p.onHand(), reserved.getOrDefault(p.id(), BigDecimal.ZERO))))
                .filter(view -> !lowOnly || view.low())
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductView product(UUID id) {
        return view(products.findById(id).orElseThrow(ProductNotFoundException::new));
    }

    /// A new product starts with nothing on hand: stock comes in with a purchase order or an adjustment.
    ///
    /// @throws DuplicateSkuException when another product of the workshop has the same SKU
    @Transactional
    public ProductView create(ProductDetails details) {
        requireFreeSku(details.sku(), null);
        return view(products.save(new Product(UUID.randomUUID(), details)));
    }

    /// Changes the details; the quantity on hand stays.
    ///
    /// @throws com.asp.platform.shared.StaleDataException when someone else saved the product since it was read
    @Transactional
    public ProductView update(UUID id, ProductDetails details, long expectedVersion) {
        Product product = locked(id);
        product.requireVersion(expectedVersion);
        requireFreeSku(details.sku(), id);
        product.update(details);
        return view(products.save(product));
    }

    /// Corrects the quantity on hand after a count, a breakage or a loss, with a reason.
    ///
    /// @param quantity positive to add, negative to take away
    /// @throws com.asp.platform.stock.domain.InsufficientStockException        when it would leave less than nothing
    /// @throws com.asp.platform.stock.domain.AdjustmentReasonRequiredException when the reason is blank
    @Transactional
    public ProductView adjust(UUID id, BigDecimal quantity, String reason, UUID userId) {
        Product product = locked(id);
        StockMovement movement = product.record(UUID.randomUUID(), MovementType.ADJUSTMENT, quantity, null, reason,
                userId, clock.instant());
        Product saved = products.save(product);
        movements.add(movement);
        return view(saved);
    }

    /// The product's ledger, newest first, with who made each movement.
    @Transactional(readOnly = true)
    public List<MovementView> movements(UUID tenantId, UUID productId) {
        products.findById(productId).orElseThrow(ProductNotFoundException::new);
        Map<UUID, String> names = directory.memberNames(tenantId);
        return movements.findByProduct(productId, MOVEMENT_LIMIT).stream()
                .map(m -> new MovementView(m, names.get(m.changedBy())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StockItem item(UUID productId) {
        Product product = products.findById(productId).orElseThrow(ProductNotFoundException::new);
        product.requireActive();
        return stockItem(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, StockItem> items(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return products.findAllById(productIds).stream()
                .collect(Collectors.toUnmodifiableMap(Product::id, StockService::stockItem, (a, b) -> a));
    }

    private static StockItem stockItem(Product product) {
        ProductDetails d = product.details();
        return new StockItem(product.id(), d.sku(), d.name(), d.unit(), d.salePriceCents(), d.vatRate(), d.active());
    }

    @Override
    @Transactional
    public void reserve(UUID productId, BigDecimal quantity, ReservationSource source, UUID sourceId,
            UUID sourceLineId) {
        Product product = locked(productId);
        StockLevel level = new StockLevel(product.onHand(), reservations.reservedQuantity(productId));
        reservations.save(Reservation.reserve(UUID.randomUUID(), product, level, quantity, source, sourceId,
                sourceLineId));
    }

    @Override
    @Transactional
    public void releaseLine(ReservationSource source, UUID sourceLineId) {
        reservations.findActiveByLine(source, sourceLineId).ifPresent(this::release);
    }

    @Override
    @Transactional
    public void releaseAll(ReservationSource source, UUID sourceId) {
        reservations.findActiveBySource(source, sourceId).forEach(this::release);
    }

    /// Reservations come ordered by product, so products are locked in one order and two deliveries cannot
    /// deadlock.
    @Override
    @Transactional
    public void consumeAll(ReservationSource source, UUID sourceId, UUID changedBy) {
        StockReference reference = new StockReference(source.name(), sourceId);
        for (Reservation reservation : reservations.findActiveBySource(source, sourceId)) {
            Product product = locked(reservation.productId());
            StockMovement movement = product.record(UUID.randomUUID(), MovementType.ISSUE,
                    reservation.quantity().negate(), reference, null, changedBy, clock.instant());
            reservation.consume();
            products.save(product);
            movements.add(movement);
            reservations.save(reservation);
        }
    }

    private void release(Reservation reservation) {
        reservation.release();
        reservations.save(reservation);
    }

    private Product locked(UUID id) {
        return products.lock(id).orElseThrow(ProductNotFoundException::new);
    }

    private ProductView view(Product product) {
        return new ProductView(product, new StockLevel(product.onHand(), reservations.reservedQuantity(product.id())));
    }

    private void requireFreeSku(String sku, @Nullable UUID exceptId) {
        if (products.skuTaken(sku, exceptId)) {
            throw new DuplicateSkuException(sku);
        }
    }

    private static @Nullable String blankToNull(@Nullable String query) {
        return query == null || query.isBlank() ? null : query.strip();
    }
}
