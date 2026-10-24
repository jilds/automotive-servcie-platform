package com.asp.platform.catalogue.domain;

/// How a service's price before VAT is set.
public enum ServicePricing {

    /// The service's own fixed price.
    FIXED,

    /// Standard hours times the workshop's labour rate, as it is when the price is needed.
    HOURLY
}
