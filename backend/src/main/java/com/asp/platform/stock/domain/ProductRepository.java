package com.asp.platform.stock.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Row-level security limits every query to the current workshop.
public interface ProductRepository {

    /// @throws DuplicateSkuException when another product of the workshop has the same SKU
    Product save(Product product);

    Optional<Product> findById(UUID id);

    /// Reads the product and locks its row until the transaction ends, so its stock can be checked and changed
    /// without a parallel request doing the same. Waits a few seconds at most for another holder of the lock.
    Optional<Product> lock(UUID id);

    /// One query; ids not found are left out.
    List<Product> findAllById(Collection<UUID> ids);

    /// By name; `query` matches SKU, name or brand, any case; null lists everything.
    List<Product> search(@Nullable String query, boolean activeOnly);

    /// Active products listed online, by name, with their reserved quantity, in one query; at most `limit`.
    /// `query` matches SKU, name or brand, any case; null lists them all.
    List<ProductStock> onlineStock(@Nullable String query, int limit);

    /// True when a product other than `exceptId` has this SKU, case ignored.
    boolean skuTaken(String sku, @Nullable UUID exceptId);
}
