package com.asp.platform.stock.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.asp.platform.shared.persistence.Constraints;
import com.asp.platform.shared.persistence.Upserts;
import com.asp.platform.stock.domain.DuplicateSkuException;
import com.asp.platform.stock.domain.Product;
import com.asp.platform.stock.domain.ProductRepository;

@Repository
class JpaProductRepository implements ProductRepository {

    private static final String UNIQUE_SKU_CONSTRAINT = "product_tenant_sku_key";

    private final SpringDataProductRepository jpa;
    private final EntityManager entityManager;

    JpaProductRepository(SpringDataProductRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    /// Flushes at once: the service's "SKU already taken?" check runs before the write, so two saves of the same
    /// SKU at the same time are only caught by the unique index, which becomes a 409 here instead of a 500.
    @Override
    public Product save(Product product) {
        try {
            return Upserts.upsert(jpa, product.id(), product, ProductEntity::new);
        } catch (DataIntegrityViolationException e) {
            if (Constraints.violated(e, UNIQUE_SKU_CONSTRAINT)) {
                throw new DuplicateSkuException(product.details().sku());
            }
            throw e;
        }
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpa.findById(id).map(ProductEntity::toDomain);
    }

    /// The product may already be in this transaction's persistence context (read before the lock), so it is
    /// reloaded once the row is locked: the stock checked under the lock is the stored one.
    @Override
    public Optional<Product> lock(UUID id) {
        return jpa.lockById(id).map(entity -> {
            entityManager.refresh(entity);
            return entity.toDomain();
        });
    }

    @Override
    public List<Product> findAllById(Collection<UUID> ids) {
        return jpa.findAllById(ids).stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public List<Product> search(@Nullable String query, boolean activeOnly) {
        return jpa.search(query, activeOnly).stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public boolean skuTaken(String sku, @Nullable UUID exceptId) {
        return exceptId == null ? jpa.skuTaken(sku) : jpa.skuTakenByOther(sku, exceptId);
    }
}
