package com.asp.platform.catalogue.infrastructure;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.asp.platform.catalogue.domain.LabourRate;
import com.asp.platform.identity.WorkshopDirectory;

/// The labour rate of the workshop the connection acts for: the same `app.tenant_id` setting row-level security
/// filters on (ADR 0002), read from the transaction's connection, then the rate from the identity module.
@Component
class WorkshopLabourRate implements LabourRate {

    private final EntityManager entityManager;
    private final WorkshopDirectory directory;

    WorkshopLabourRate(EntityManager entityManager, WorkshopDirectory directory) {
        this.entityManager = entityManager;
        this.directory = directory;
    }

    @Override
    public long hourlyCents() {
        Object tenant = entityManager.createNativeQuery("select current_setting('app.tenant_id', true)")
                .getSingleResult();
        if (!(tenant instanceof String id) || id.isBlank()) {
            throw new IllegalStateException("No workshop on this connection");
        }
        return directory.labourRateCents(UUID.fromString(id));
    }
}
