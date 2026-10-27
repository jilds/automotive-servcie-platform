package com.asp.platform.shop.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.shared.ConflictException;
import com.asp.platform.shared.NotFoundException;
import com.asp.platform.shared.PortugueseNif;
import com.asp.platform.shared.StatusTokens;
import com.asp.platform.shop.OrderPlaced;
import com.asp.platform.shop.domain.OutOfStockException;
import com.asp.platform.shop.domain.ProductNotListedException;
import com.asp.platform.shop.domain.ShopBusyException;
import com.asp.platform.shop.domain.ShopContact;
import com.asp.platform.shop.domain.ShopOrder;
import com.asp.platform.shop.domain.ShopOrderLine;
import com.asp.platform.shop.domain.ShopOrderNotFoundException;
import com.asp.platform.shop.domain.ShopOrderRepository;
import com.asp.platform.shop.domain.ShopQueryTooLongException;
import com.asp.platform.shop.domain.ShopSettings;
import com.asp.platform.shop.domain.ShopSettingsRepository;
import com.asp.platform.stock.ReservationSource;
import com.asp.platform.stock.StockItem;
import com.asp.platform.stock.StockReservations;

/// The shop pages of a workshop's public site. Runs inside the public request's
/// [com.asp.platform.shared.TenantScope], so row-level security keeps every query inside the workshop of the slug.
///
/// A new order reserves every line in the stock module's lock order ([StockReservations#LOCK_ORDER]), then takes
/// the workshop's shop settings lock (numbers without gaps, and the cap on open orders), and stores the order, all
/// in one transaction: when one line cannot be reserved nothing is stored, and two orders for the last unit give
/// one order and one 409 `insufficient-stock`. The settings lock is taken last, so it is held only for the few
/// statements that number and store the order, never while waiting for a product.
@Service
public class PublicShopService {

    /// Codes of the stock module's reservation errors (`StockReservations#reserve`).
    private static final String STOCK_INSUFFICIENT = "insufficient-stock";
    private static final String STOCK_PRODUCT_INACTIVE = "product-inactive";

    /// Lines in the stock module's lock order.
    private static final Comparator<ShopOrderLine> PRODUCT_ORDER =
            Comparator.comparing(ShopOrderLine::productId, StockReservations.LOCK_ORDER);

    private final ShopOrderRepository orders;
    private final ShopSettingsRepository settings;
    private final StockReservations stock;
    private final ShopProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PublicShopService(ShopOrderRepository orders, ShopSettingsRepository settings, StockReservations stock,
            ShopProperties properties, ApplicationEventPublisher events, Clock clock) {
        this.orders = orders;
        this.settings = settings;
        this.stock = stock;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /// The offered products by name (at most 200), with their price with VAT and availability.
    ///
    /// @param query part of the name, brand or SKU, at most 60 characters
    /// @throws ShopQueryTooLongException for a longer query
    @Transactional(readOnly = true)
    public List<PublicProduct> products(@Nullable String query) {
        if (query != null && query.strip().length() > ShopQueryTooLongException.MAX) {
            throw new ShopQueryTooLongException();
        }
        return stock.onlineProducts(query).stream().map(PublicProduct::of).toList();
    }

    /// Stores a PLACED order with a snapshot of the contact and the products and reserves its stock, and returns
    /// the status token once.
    ///
    /// @param country the workshop's country: a Portuguese workshop needs a valid NIF when one is given
    /// @throws com.asp.platform.shared.InvalidNifException for an invalid NIF of a Portuguese workshop
    /// @throws ProductNotListedException                   when a product is not offered online
    /// @throws OutOfStockException                          when less of a product is available than ordered
    /// @throws ShopBusyException                            when the workshop holds `app.shop.max-open-orders` open orders
    @Transactional
    public ShopReceipt place(UUID tenantId, String country, PlaceOrderCommand command) {
        PortugueseNif.check(command.nif(), country);
        ShopContact contact = new ShopContact(command.name(), command.phone(), command.email(), command.nif());
        List<ShopOrderLine> lines = lines(command.lines());
        UUID orderId = UUID.randomUUID();
        lines.stream().sorted(PRODUCT_ORDER).forEach(line -> reserve(orderId, line));
        ShopSettings current = settings.lockOrCreate();
        if (orders.countOpen() >= properties.maxOpenOrders()) {
            throw new ShopBusyException();
        }
        String token = StatusTokens.generate();
        Instant now = clock.instant();
        ShopOrder saved = orders.save(ShopOrder.place(orderId, orders.nextNumber(), contact, command.language(),
                command.marketingConsent(), StatusTokens.hash(token), lines, now, current.holdDays()));
        String email = contact.email();
        if (email != null) {
            events.publishEvent(new OrderPlaced(tenantId, saved.number(), contact.name(), email, saved.language(),
                    saved.totalCents(), saved.holdUntil(), token));
        }
        return new ShopReceipt(saved.number(), token, saved.totalCents(), saved.holdUntil());
    }

    /// @throws ShopOrderNotFoundException for an unknown token
    @Transactional(readOnly = true)
    public PublicOrderView status(String token, String workshopName, ZoneId zone) {
        return view(byToken(token), workshopName, zone);
    }

    /// The customer cancels on the status link while the order is PLACED; the stock is released. No mail: the
    /// customer did it.
    ///
    /// @throws ShopOrderNotFoundException                              for an unknown token
    /// @throws com.asp.platform.shop.domain.ShopOrderNotOpenException once the order is READY or closed
    @Transactional
    public PublicOrderView cancel(String token, String workshopName, ZoneId zone) {
        ShopOrder order = byToken(token);
        order.cancelByCustomer(clock.instant());
        stock.releaseAll(ReservationSource.SHOP_ORDER, order.id());
        return view(orders.save(order), workshopName, zone);
    }

    private PublicOrderView view(ShopOrder order, String workshopName, ZoneId zone) {
        String note = settings.find().map(ShopSettings::pickupNote).orElse(null);
        return new PublicOrderView(order, workshopName, zone, note);
    }

    private ShopOrder byToken(String token) {
        return orders.findByTokenHash(StatusTokens.hash(token)).orElseThrow(ShopOrderNotFoundException::new);
    }

    /// The lines in the order the customer gave them, each priced from the product now. Every product must be
    /// active and listed online.
    private List<ShopOrderLine> lines(List<PlaceOrderCommand.Line> requested) {
        Map<UUID, StockItem> items = stock.items(requested.stream().map(PlaceOrderCommand.Line::productId).toList());
        List<ShopOrderLine> lines = new ArrayList<>(requested.size());
        for (PlaceOrderCommand.Line line : requested) {
            StockItem item = items.get(line.productId());
            if (item == null || !item.active() || !item.listedOnline()) {
                throw new ProductNotListedException(line.productId());
            }
            lines.add(new ShopOrderLine(UUID.randomUUID(), item.id(), item.sku(), item.name(), item.unit(),
                    line.quantity(), item.salePriceCents(), item.vatRate()));
        }
        return List.copyOf(lines);
    }

    /// The stock module's errors name no product; the shop's answer does. They are told apart by their stable code,
    /// so the shop does not reach into the stock module's internals.
    private void reserve(UUID orderId, ShopOrderLine line) {
        try {
            stock.reserve(line.productId(), BigDecimal.valueOf(line.quantity()), ReservationSource.SHOP_ORDER, orderId,
                    line.id());
        } catch (ConflictException e) {
            switch (e.code()) {
                case STOCK_INSUFFICIENT -> throw new OutOfStockException(line.productId());
                case STOCK_PRODUCT_INACTIVE -> throw new ProductNotListedException(line.productId());
                default -> throw e;
            }
        } catch (NotFoundException e) {
            throw new ProductNotListedException(line.productId());
        }
    }
}
