package com.asp.platform.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.asp.platform.identity.domain.PlatformAdmin;
import com.asp.platform.identity.domain.PlatformAdminRepository;

@Repository
class JpaPlatformAdminRepository implements PlatformAdminRepository {

    private final SpringDataPlatformAdminRepository jpa;

    JpaPlatformAdminRepository(SpringDataPlatformAdminRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PlatformAdmin save(PlatformAdmin admin) {
        PlatformAdminEntity entity = jpa.findById(admin.id())
                .map(existing -> {
                    existing.apply(admin);
                    return existing;
                })
                .orElseGet(() -> jpa.save(new PlatformAdminEntity(admin)));
        return entity.toDomain();
    }

    @Override
    public Optional<PlatformAdmin> findById(UUID id) {
        return jpa.findById(id).map(PlatformAdminEntity::toDomain);
    }

    @Override
    public Optional<PlatformAdmin> findByEmail(String normalizedEmail) {
        return jpa.findByEmail(normalizedEmail).map(PlatformAdminEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return jpa.existsByEmail(normalizedEmail);
    }

    @Override
    public boolean existsAny() {
        return jpa.count() > 0;
    }
}

interface SpringDataPlatformAdminRepository extends JpaRepository<PlatformAdminEntity, UUID> {

    Optional<PlatformAdminEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
