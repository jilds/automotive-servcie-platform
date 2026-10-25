package com.asp.platform.catalogue.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.asp.platform.catalogue.domain.ServicePackage;
import com.asp.platform.catalogue.domain.ServicePackageRepository;
import com.asp.platform.shared.persistence.Upserts;

@Repository
class JpaServicePackageRepository implements ServicePackageRepository {

    private final SpringDataServicePackageRepository jpa;

    JpaServicePackageRepository(SpringDataServicePackageRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ServicePackage save(ServicePackage servicePackage) {
        return Upserts.upsert(jpa, servicePackage.id(), servicePackage, ServicePackageEntity::new);
    }

    @Override
    public Optional<ServicePackage> findById(UUID id) {
        return jpa.findById(id).map(ServicePackageEntity::toDomain);
    }

    @Override
    public List<ServicePackage> search(@Nullable String query, boolean activeOnly) {
        return jpa.search(query, activeOnly).stream().map(ServicePackageEntity::toDomain).toList();
    }
}
