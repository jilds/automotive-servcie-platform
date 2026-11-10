package com.asp.platform.billing.domain;

import org.jspecify.annotations.Nullable;

/// The workshop as it was when the document was issued.
public record IssuerSnapshot(String name, @Nullable String nif, String country) {
}
