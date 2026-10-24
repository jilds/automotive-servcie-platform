package com.asp.platform.catalogue;

import java.util.List;
import java.util.UUID;

/// Public API of the catalogue module: the work order lines a service or a package becomes.
public interface CatalogueLookup {

    /// One LABOR line: the service at its price.
    ///
    /// @throws com.asp.platform.catalogue.domain.ServiceNotFoundException when the workshop has no such service
    /// @throws com.asp.platform.catalogue.domain.ServiceInactiveException when it is no longer offered
    List<CatalogueLine> linesForService(UUID serviceId);

    /// One LABOR line per service of the package and one PART line per product (two when its share does not divide
    /// evenly by its quantity: some units one cent dearer). The package price is spread over the items in proportion
    /// to their own prices (services at their effective price, products at their sale price, times the quantity),
    /// rounded half up, with each product's share in whole cents per unit where possible and the remainder on the last
    /// service or single product, so the lines add up to exactly the package price; every line takes the package's
    /// VAT rate. The caller reserves the PART lines' quantities; a product no longer sold is refused there.
    ///
    /// @throws com.asp.platform.catalogue.domain.PackageNotFoundException when the workshop has no such package
    /// @throws com.asp.platform.catalogue.domain.PackageInactiveException when it is no longer offered
    List<CatalogueLine> linesForPackage(UUID packageId);
}
