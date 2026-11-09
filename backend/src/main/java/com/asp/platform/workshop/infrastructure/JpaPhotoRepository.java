package com.asp.platform.workshop.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.shared.persistence.Upserts;
import com.asp.platform.workshop.domain.PhotoRepository;
import com.asp.platform.workshop.domain.WorkOrderPhoto;

@Repository
class JpaPhotoRepository implements PhotoRepository {

    private final SpringDataPhotoRepository jpa;
    private final JdbcClient jdbc;

    JpaPhotoRepository(SpringDataPhotoRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public void lockStorageQuota(UUID tenantId) {
        jdbc.sql("select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) l")
                .param("key", "photo-quota:" + tenantId)
                .query(Integer.class).single();
    }

    @Override
    public WorkOrderPhoto save(WorkOrderPhoto photo) {
        return Upserts.upsert(jpa, photo.id(), photo, PhotoEntity::new);
    }

    @Override
    public Optional<WorkOrderPhoto> findById(UUID id) {
        return jpa.findById(id).map(PhotoEntity::toDomain);
    }

    @Override
    public List<WorkOrderPhoto> findByWorkOrder(UUID workOrderId) {
        return jpa.findByWorkOrderIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(workOrderId).stream()
                .map(PhotoEntity::toDomain).toList();
    }

    @Override
    public int countByWorkOrder(UUID workOrderId) {
        return jpa.countByWorkOrderIdAndDeletedAtIsNull(workOrderId);
    }

    @Override
    public long storedBytes() {
        return jpa.storedBytes();
    }

    @Override
    public List<UUID> findPurgeDueIds(Instant cutoff, int limit) {
        return jpa.findPurgeDueIds(cutoff, limit);
    }
}
