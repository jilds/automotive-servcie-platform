package com.asp.platform.catalogue.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.catalogue.CatalogueLine;
import com.asp.platform.catalogue.CatalogueLookup;
import com.asp.platform.catalogue.ServiceOffer;
import com.asp.platform.catalogue.domain.LabourRate;
import com.asp.platform.catalogue.domain.PackageDetails;
import com.asp.platform.catalogue.domain.PackageItem;
import com.asp.platform.catalogue.domain.PackageItemKind;
import com.asp.platform.catalogue.domain.PackageNotFoundException;
import com.asp.platform.catalogue.domain.PackagePricing;
import com.asp.platform.catalogue.domain.PackageProduct;
import com.asp.platform.catalogue.domain.PackageProducts;
import com.asp.platform.catalogue.domain.ServiceDetails;
import com.asp.platform.catalogue.domain.ServiceItem;
import com.asp.platform.catalogue.domain.ServiceItemRepository;
import com.asp.platform.catalogue.domain.ServiceNotFoundException;
import com.asp.platform.catalogue.domain.ServicePackage;
import com.asp.platform.catalogue.domain.ServicePackageRepository;

/// The workshop's services and packages. Row-level security keeps every query inside the signed-in workshop, so a
/// service of another workshop reads as not found. Hourly services are priced at the workshop's labour rate as it is
/// when the price is read or a line is added; the rate is only looked up when an hourly service is involved. Packages
/// may also hold stock products, read through the stock module at their sale price.
@Service
public class CatalogueService implements CatalogueLookup {

    private final ServiceItemRepository services;
    private final ServicePackageRepository packages;
    private final LabourRate labourRate;
    private final PackageProducts products;

    CatalogueService(ServiceItemRepository services, ServicePackageRepository packages, LabourRate labourRate,
            PackageProducts products) {
        this.services = services;
        this.packages = packages;
        this.labourRate = labourRate;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<ServiceView> services(@Nullable String query, boolean activeOnly) {
        List<ServiceItem> found = services.search(blankToNull(query), activeOnly);
        LongSupplier rate = rateFor(found);
        return found.stream().map(item -> view(item, rate)).toList();
    }

    @Transactional(readOnly = true)
    public ServiceView service(UUID id) {
        return view(find(id));
    }

    @Transactional
    public ServiceView createService(ServiceDetails details) {
        return view(services.save(new ServiceItem(UUID.randomUUID(), details)));
    }

    /// @throws com.asp.platform.shared.StaleDataException when someone else saved the service since it was read
    @Transactional
    public ServiceView updateService(UUID id, ServiceDetails details, long expectedVersion) {
        ServiceItem item = find(id);
        item.requireVersion(expectedVersion);
        item.update(details);
        return view(services.save(item));
    }

    @Transactional(readOnly = true)
    public List<PackageView> packages(@Nullable String query, boolean activeOnly) {
        List<ServicePackage> found = packages.search(blankToNull(query), activeOnly);
        Contents contents = contentsOf(found.stream().flatMap(p -> p.details().items().stream()).toList());
        return found.stream().map(p -> view(p, contents)).toList();
    }

    @Transactional(readOnly = true)
    public PackageView packageView(UUID id) {
        ServicePackage found = servicePackage(id);
        return view(found, contentsOf(found.details().items()));
    }

    /// The services and products must belong to the workshop and still be offered.
    @Transactional
    public PackageView createPackage(PackageDetails details) {
        requireNewItemsActive(details, requireContents(details), Set.of());
        ServicePackage saved = packages.save(new ServicePackage(UUID.randomUUID(), details));
        return packageView(saved.id());
    }

    /// Services and products already in the package may have been deactivated since; only newly added ones must be
    /// active.
    ///
    /// @throws com.asp.platform.shared.StaleDataException when someone else saved the package since it was read
    @Transactional
    public PackageView updatePackage(UUID id, PackageDetails details, long expectedVersion) {
        ServicePackage found = servicePackage(id);
        found.requireVersion(expectedVersion);
        Set<PackageItem.Key> before = found.details().items().stream().map(PackageItem::key)
                .collect(Collectors.toSet());
        requireNewItemsActive(details, requireContents(details), before);
        found.update(details);
        packages.save(found);
        return packageView(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceOffer> onlineServices() {
        List<ServiceItem> listed = services.search(null, true).stream()
                .filter(item -> item.details().listedOnline())
                .toList();
        LongSupplier rate = rateFor(listed);
        return listed.stream().map(item -> offer(item, rate)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ServiceOffer> services(Collection<UUID> serviceIds) {
        if (serviceIds.isEmpty()) {
            return Map.of();
        }
        List<ServiceItem> found = services.findAllById(serviceIds);
        LongSupplier rate = rateFor(found);
        return found.stream().collect(Collectors.toUnmodifiableMap(ServiceItem::id, item -> offer(item, rate),
                (a, b) -> a));
    }

    private static ServiceOffer offer(ServiceItem item, LongSupplier rate) {
        ServiceDetails d = item.details();
        return new ServiceOffer(item.id(), d.name(), d.description(), d.category(), d.durationMinutes(),
                d.effectivePriceCents(rate.getAsLong()), d.vatRate(), d.active(), d.listedOnline());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogueLine> linesForService(UUID serviceId) {
        ServiceItem item = find(serviceId);
        item.requireActive();
        ServiceDetails d = item.details();
        return List.of(new CatalogueLine(item.id(), d.name(), BigDecimal.ONE, view(item).effectivePriceCents(),
                d.vatRate()));
    }

    /// A product of the package that is no longer sold stops the whole package (409 `product-inactive`).
    @Override
    @Transactional(readOnly = true)
    public List<CatalogueLine> linesForPackage(UUID packageId) {
        ServicePackage found = servicePackage(packageId);
        found.requireActive();
        PackageDetails d = found.details();
        Contents contents = requireContents(d);
        for (PackageItem item : d.items()) {
            PackageProduct product = contents.products().get(item.itemId());
            if (item.kind() == PackageItemKind.PRODUCT && product != null && !product.active()) {
                products.requireForSale(item.itemId());
            }
        }
        List<PackageView.Item> priced = contents.priced(d.items());
        List<Long> shares = PackagePricing.allocate(d.priceCents(),
                priced.stream().map(item -> item.unitPriceCents() * item.quantity()).toList(),
                priced.stream().map(item -> item.kind() == PackageItemKind.PRODUCT ? item.quantity() : 1).toList());
        List<CatalogueLine> lines = new ArrayList<>();
        for (int i = 0; i < priced.size(); i++) {
            PackageView.Item item = priced.get(i);
            if (item.kind() == PackageItemKind.SERVICE) {
                String description = d.name() + ": " + item.name()
                        + (item.quantity() > 1 ? " × " + item.quantity() : "");
                lines.add(new CatalogueLine(item.id(), description, BigDecimal.ONE, shares.get(i), d.vatRate()));
            } else {
                String description = d.name() + ": " + item.name() + " (" + item.sku() + ")";
                for (PackagePricing.Portion portion : PackagePricing.split(shares.get(i), item.quantity())) {
                    lines.add(CatalogueLine.part(item.id(), description, BigDecimal.valueOf(portion.quantity()),
                            portion.unitCents(), d.vatRate()));
                }
            }
        }
        return List.copyOf(lines);
    }

    private ServiceItem find(UUID id) {
        return services.findById(id).orElseThrow(ServiceNotFoundException::new);
    }

    private ServiceView view(ServiceItem item) {
        return view(item, rateFor(List.of(item)));
    }

    private static ServiceView view(ServiceItem item, LongSupplier rate) {
        return new ServiceView(item, item.details().effectivePriceCents(rate.getAsLong()));
    }

    /// The labour rate, looked up at most once and only when one of the services is priced by the hour.
    private LongSupplier rateFor(Collection<ServiceItem> items) {
        if (items.stream().noneMatch(item -> item.details().hourly())) {
            return () -> 0;
        }
        long rate = labourRate.hourlyCents();
        return () -> rate;
    }

    private ServicePackage servicePackage(UUID id) {
        return packages.findById(id).orElseThrow(PackageNotFoundException::new);
    }

    /// Items not in the package before must still be offered (409 `service-inactive`, `product-inactive`).
    private void requireNewItemsActive(PackageDetails details, Contents contents, Set<PackageItem.Key> before) {
        for (PackageItem item : details.items()) {
            if (before.contains(item.key())) {
                continue;
            }
            ServiceItem service = contents.services().get(item.itemId());
            PackageProduct product = contents.products().get(item.itemId());
            if (item.kind() == PackageItemKind.SERVICE && service != null) {
                service.requireActive();
            } else if (item.kind() == PackageItemKind.PRODUCT && product != null && !product.active()) {
                products.requireForSale(item.itemId());
            }
        }
    }

    /// The services and products of the items, each of which must be in the workshop.
    ///
    /// @throws ServiceNotFoundException when a service is not in the workshop (404 `service-not-found`)
    /// @throws com.asp.platform.shared.NotFoundException when a product is not (404 `product-not-found`)
    private Contents requireContents(PackageDetails details) {
        Contents contents = contentsOf(details.items());
        for (PackageItem item : details.items()) {
            if (item.kind() == PackageItemKind.SERVICE && !contents.services().containsKey(item.itemId())) {
                throw new ServiceNotFoundException();
            }
            if (item.kind() == PackageItemKind.PRODUCT && !contents.products().containsKey(item.itemId())) {
                products.requireForSale(item.itemId()); // throws product-not-found
                throw new IllegalStateException("Product " + item.itemId() + " was not found");
            }
        }
        return contents;
    }

    /// Reads the services and products of the items: one query each, and the labour rate only when needed.
    private Contents contentsOf(List<PackageItem> items) {
        List<UUID> serviceIds = idsOf(items, PackageItemKind.SERVICE);
        List<UUID> productIds = idsOf(items, PackageItemKind.PRODUCT);
        Map<UUID, ServiceItem> servicesById = serviceIds.isEmpty() ? Map.of()
                : services.findAllById(serviceIds).stream()
                        .collect(Collectors.toMap(ServiceItem::id, Function.identity(), (a, b) -> a));
        Map<UUID, PackageProduct> productsById = productIds.isEmpty() ? Map.of() : products.find(productIds);
        return new Contents(servicesById, productsById, rateFor(servicesById.values()));
    }

    private static List<UUID> idsOf(List<PackageItem> items, PackageItemKind kind) {
        return items.stream().filter(item -> item.kind() == kind).map(PackageItem::itemId).distinct().toList();
    }

    private static PackageView view(ServicePackage found, Contents contents) {
        List<PackageView.Item> items = contents.priced(found.details().items());
        long list = items.stream().mapToLong(item -> item.unitPriceCents() * item.quantity()).sum();
        return new PackageView(found, items, list);
    }

    private static @Nullable String blankToNull(@Nullable String query) {
        return query == null || query.isBlank() ? null : query.strip();
    }

    /// What packages hold, read once for one or several packages.
    private record Contents(Map<UUID, ServiceItem> services, Map<UUID, PackageProduct> products, LongSupplier rate) {

        /// Each item with its name and own price, in package order; items no longer found are left out.
        List<PackageView.Item> priced(List<PackageItem> items) {
            List<PackageView.Item> result = new ArrayList<>();
            for (PackageItem item : items) {
                if (item.kind() == PackageItemKind.SERVICE) {
                    ServiceItem service = services.get(item.itemId());
                    if (service != null) {
                        ServiceDetails d = service.details();
                        result.add(new PackageView.Item(PackageItemKind.SERVICE, service.id(), d.name(), null,
                                item.quantity(), d.effectivePriceCents(rate.getAsLong()), d.active()));
                    }
                } else {
                    PackageProduct product = products.get(item.itemId());
                    if (product != null) {
                        result.add(new PackageView.Item(PackageItemKind.PRODUCT, product.id(), product.name(),
                                product.sku(), item.quantity(), product.salePriceCents(), product.active()));
                    }
                }
            }
            return List.copyOf(result);
        }
    }
}
