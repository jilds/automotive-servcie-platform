package com.asp.platform.catalogue.infrastructure;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.asp.platform.catalogue.domain.PackageItem;

/// A service or a stock product of a package; the database checks that exactly one of the two is set (V13).
/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security.
@Entity
@Table(name = "service_package_item")
class ServicePackageItemEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false, updatable = false)
    private ServicePackageEntity servicePackage;

    @Column(name = "service_item_id", updatable = false)
    private @Nullable UUID serviceItemId;

    @Column(name = "product_id", updatable = false)
    private @Nullable UUID productId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int quantity;

    @SuppressWarnings("NullAway.Init")
    protected ServicePackageItemEntity() {
    }

    ServicePackageItemEntity(ServicePackageEntity servicePackage, PackageItem item, int position) {
        this.id = UUID.randomUUID();
        this.servicePackage = servicePackage;
        this.serviceItemId = item.serviceItemId();
        this.productId = item.productId();
        update(item, position);
    }

    void update(PackageItem item, int newPosition) {
        this.quantity = item.quantity();
        this.position = newPosition;
    }

    PackageItem.Key key() {
        return toDomain().key();
    }

    int position() {
        return position;
    }

    PackageItem toDomain() {
        return new PackageItem(serviceItemId, productId, quantity);
    }
}
