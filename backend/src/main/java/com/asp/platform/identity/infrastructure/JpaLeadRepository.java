package com.asp.platform.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.asp.platform.identity.domain.Lead;
import com.asp.platform.identity.domain.LeadRepository;
import com.asp.platform.shared.persistence.Upserts;

@Repository
class JpaLeadRepository implements LeadRepository {

    private final SpringDataLeadRepository jpa;

    JpaLeadRepository(SpringDataLeadRepository jpa) {
        this.jpa = jpa;
    }

    /// Flushes, so the returned version is the stored one.
    @Override
    public Lead save(Lead lead) {
        return Upserts.upsert(jpa, lead.id(), lead, LeadEntity::new);
    }

    @Override
    public Optional<Lead> findById(UUID id) {
        return jpa.findById(id).map(LeadEntity::toDomain);
    }

    @Override
    public List<Lead> list(Lead.@Nullable Status status, int limit) {
        List<LeadEntity> rows = status == null
                ? jpa.findAllByOrderByCreatedAtDesc(Limit.of(limit))
                : jpa.findByStatusOrderByCreatedAtDesc(status, Limit.of(limit));
        return rows.stream().map(LeadEntity::toDomain).toList();
    }
}

interface SpringDataLeadRepository extends JpaRepository<LeadEntity, UUID> {

    List<LeadEntity> findAllByOrderByCreatedAtDesc(Limit limit);

    List<LeadEntity> findByStatusOrderByCreatedAtDesc(Lead.Status status, Limit limit);
}
