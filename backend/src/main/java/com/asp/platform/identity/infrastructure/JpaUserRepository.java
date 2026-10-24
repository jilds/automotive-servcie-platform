package com.asp.platform.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserRepository;
import com.asp.platform.shared.Role;
import com.asp.platform.shared.persistence.Upserts;

@Repository
class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository jpa;

    JpaUserRepository(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public User save(User user) {
        return Upserts.upsert(jpa, user.id(), user, UserEntity::new);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String normalizedEmail) {
        return jpa.findByEmail(normalizedEmail).map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return jpa.existsByEmail(normalizedEmail);
    }

    @Override
    public List<User> findByTenant(UUID tenantId) {
        return jpa.findByTenantIdOrderByName(tenantId).stream().map(UserEntity::toDomain).toList();
    }

    @Override
    public Optional<User> findFirstOwner(UUID tenantId) {
        return jpa.findFirstByTenantIdAndRoleOrderByCreatedAtAscIdAsc(tenantId, Role.OWNER).map(UserEntity::toDomain);
    }

    @Override
    public List<User> lockTeam(UUID tenantId) {
        return jpa.lockByTenantId(tenantId).stream().map(UserEntity::toDomain).toList();
    }
}
