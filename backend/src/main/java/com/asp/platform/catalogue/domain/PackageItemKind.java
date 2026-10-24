package com.asp.platform.catalogue.domain;

/// What a package item is: a service of the catalogue or a product from stock.
public enum PackageItemKind {

    /// A standard service; becomes a LABOR line on the work order.
    SERVICE,

    /// A stock product at its sale price; becomes a PART line that reserves stock.
    PRODUCT
}
