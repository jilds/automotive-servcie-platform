package com.asp.platform.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserRepository;

@Repository
class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository jpa;

    JpaUserRepository(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public User save(User user) {
        UserEntity entity = jpa.findById(user.id())
                .map(existing -> {
                    existing.apply(user);
                    return existing;
                })
                .orElseGet(() -> jpa.save(new UserEntity(user)));
        return entity.toDomain();
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
}
