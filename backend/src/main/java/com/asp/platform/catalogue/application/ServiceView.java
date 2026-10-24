package com.asp.platform.catalogue.application;

import com.asp.platform.catalogue.domain.ServiceItem;

/// A service with its price as it is now.
///
/// @param effectivePriceCents the fixed price, or the standard hours times the workshop's current labour rate
public record ServiceView(ServiceItem item, long effectivePriceCents) {
}
