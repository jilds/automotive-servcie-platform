package com.asp.platform.catalogue.domain;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/// The signed-in workshop's stock products, as packages need them.
public interface PackageProducts {

    /// One query. Products no longer sold are included; ids the workshop does not have are left out.
    Map<UUID, PackageProduct> find(Collection<UUID> ids);

    /// A product that may be added to a package.
    ///
    /// @throws com.asp.platform.shared.NotFoundException when the workshop has no such product (`product-not-found`)
    /// @throws com.asp.platform.shared.ConflictException when it is no longer sold (`product-inactive`)
    PackageProduct requireForSale(UUID id);
}
